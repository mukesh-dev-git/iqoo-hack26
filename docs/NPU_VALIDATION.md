# NPU-vs-CPU validation (pre-event, AMD hardware)

We don't have Snapdragon/Hexagon NPU hardware yet (iQOO phones are issued at the event), but
we wanted to validate the core thesis — that on-device NPU acceleration is real, not
decorative — on whatever NPU hardware we could get our hands on beforehand.

**Hardware:** AMD Ryzen AI 5 330 (XDNA 2 NPU, 50 TOPS, Copilot+ certified), via AMD's
Ryzen AI Software 1.8.0 (ONNX Runtime + VitisAIExecutionProvider).

**Method:** same ONNX model, same input, two execution providers, 200 timed inference runs
each (20 warmup runs discarded to exclude one-time NPU compilation cost). Script:
`npu_vs_cpu_bench.py` (adapted from AMD's `quicktest.py`).

## Results

| Provider | Mean | Median | p95 |
|---|---|---|---|
| NPU (VitisAIExecutionProvider) | 1.971 ms | 1.925 ms | 2.368 ms |
| CPU (CPUExecutionProvider) | 13.292 ms | 12.167 ms | 20.057 ms |

**~6.74x speedup on the NPU**, on this hardware, this model.

## What this does and doesn't prove

**Does:** NPU-accelerated inference via ONNX Runtime is real, measurable, and substantially
faster than CPU — the general architecture our pitch depends on (offload inference to
dedicated NPU silicon instead of CPU) holds up under an actual benchmark, not just an
assertion.

**Doesn't:** This is AMD's XDNA NPU, not Qualcomm's Hexagon NPU on the iQOO phone — different
silicon, different SDK (Ryzen AI Software / Vitis AI EP vs GenieX + AI Hub). The model here is
a small generic test CNN, not our code-review model. Treat this as "the concept is validated,
on different hardware" — not as a performance prediction for the actual device.

## Reproduce it

```powershell
$env:RYZEN_AI_INSTALLATION_PATH = "C:\Program Files\RyzenAI\1.8.0"
conda activate ryzen-ai-1.8.0
python npu_vs_cpu_bench.py
```
