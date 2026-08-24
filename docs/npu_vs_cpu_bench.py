import os
import time
import numpy as np
import onnxruntime as ort

install_dir = os.environ["RYZEN_AI_INSTALLATION_PATH"]
model = os.path.join(install_dir, "quicktest", "test_model.onnx")

session_options = ort.SessionOptions()
session_options.log_severity_level = 3  # quiet — we just want numbers

def bench(providers, label, n=200, warmup=20):
    session = ort.InferenceSession(model, sess_options=session_options, providers=providers)
    rng = np.random.rand(1, 3, 32, 32).astype(np.float32)

    for _ in range(warmup):
        session.run(None, {"input": rng})

    times = []
    for _ in range(n):
        start = time.perf_counter()
        session.run(None, {"input": rng})
        times.append((time.perf_counter() - start) * 1000)  # ms

    times = np.array(times)
    print(f"\n=== {label} ===")
    print(f"mean:   {times.mean():.3f} ms")
    print(f"median: {np.median(times):.3f} ms")
    print(f"p95:    {np.percentile(times, 95):.3f} ms")
    return times.mean()

print("Benchmarking test_model.onnx — same model, two execution providers")
print(f"Model: {model}")

npu_mean = bench(["VitisAIExecutionProvider"], "NPU (VitisAIExecutionProvider)")
cpu_mean = bench(["CPUExecutionProvider"], "CPU (CPUExecutionProvider)")

print(f"\n=== Summary ===")
print(f"NPU mean latency: {npu_mean:.3f} ms")
print(f"CPU mean latency: {cpu_mean:.3f} ms")
if npu_mean > 0:
    print(f"Speedup: {cpu_mean / npu_mean:.2f}x")
