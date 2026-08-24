"""
NPU-backed review generator — AMD Ryzen AI Software (onnxruntime-genai + VitisAIExecutionProvider).

The CLI example this is adapted from (model_chat.py) reloads the model on every invocation,
which costs ~19s per call. This module loads it once at import time and reuses the same
model/tokenizer across requests, so server.py can call `generate()` per-request cheaply.

Must run inside the `ryzen-ai-1.8.0` conda env — it's the only place `onnxruntime_genai` with
the RyzenAI execution provider is installed:
    conda activate ryzen-ai-1.8.0

Model: pre-quantized by AMD specifically for this NPU + SDK version, downloaded from
amd/Qwen2.5-Coder-1.5B-Instruct_rai_1.8.0_npu_4K on Hugging Face. See ../docs/NPU_VALIDATION.md
for how this was validated (real NPU execution confirmed via PartitionPass op placement).
"""

import json
import os
import time

import onnxruntime_genai as og

MODEL_PATH = os.environ.get("NPU_MODEL_PATH", r"D:\models\qwen2.5-coder-1.5b-npu")
SYSTEM_PROMPT = "You are a precise, terse code reviewer."
MAX_CONTEXT = 4096  # matches this model's NPU-compiled 4K context build
MAX_NEW_TOKENS = 256

# Same contract as server.py's Ollama path — same PROMPT_TEMPLATE, same FINDING_RE downstream.
PROMPT_TEMPLATE = """Review this diff for bugs and risky patterns. For each issue, output one \
line exactly as:
LINE:<n> SEVERITY:<BUG|WARNING|INFO> MSG:<one sentence>

Diff:
{diff}
"""

print(f"[npu_backend] Loading model from {MODEL_PATH} ...")
_load_start = time.time()
_config = og.Config(MODEL_PATH)
_model = og.Model(_config)
_tokenizer = og.Tokenizer(_model)
_tokenizer_stream = _tokenizer.create_stream()
print(f"[npu_backend] Model loaded in {time.time() - _load_start:.2f}s — ready.")

# apply_chat_template needs the jinja template text passed explicitly — it doesn't
# auto-read chat_template.jinja from MODEL_PATH on its own (see model_chat.py's
# apply_chat_template() helper, which does the same thing).
_jinja_path = os.path.join(MODEL_PATH, "chat_template.jinja")
with open(_jinja_path, encoding="utf-8") as _f:
    _template_str = _f.read()


def generate(diff: str) -> str:
    """Run one review generation on the NPU. Returns the raw model output text
    (still in LINE:/SEVERITY:/MSG: form — parsing happens in server.py, same as the Ollama path)."""
    user_content = PROMPT_TEMPLATE.format(diff=diff)
    messages = json.dumps(
        [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_content},
        ]
    )
    prompt = _tokenizer.apply_chat_template(
        messages=messages, add_generation_prompt=True, template_str=_template_str
    )
    input_tokens = _tokenizer.encode(prompt)

    params = og.GeneratorParams(_model)
    params.set_search_options(
        batch_size=1,
        max_length=min(len(input_tokens) + MAX_NEW_TOKENS, MAX_CONTEXT),
        do_sample=False,  # deterministic — predictable output for a demo
    )
    generator = og.Generator(_model, params)
    generator.append_tokens(input_tokens)

    output_tokens = []
    while not generator.is_done():
        generator.generate_next_token()
        output_tokens.append(generator.get_next_tokens()[0])

    return "".join(_tokenizer_stream.decode(t) for t in output_tokens)
