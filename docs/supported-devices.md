# Supported devices and tested limits

- A **64-bit Android device** (ARM64 or x86-64) is required by the AI runtime. 32-bit devices are excluded from installation.
- Installation minimum: Android 8.0 (API 26). Encrypted database access requires Android 12 or newer; older versions can use unencrypted databases and general chat where the model/runtime works.
- Local inference needs a compatible CPU/GPU, enough memory and a supported model bundle. Installation alone does not guarantee a model will run.
- Models vary substantially in size. Installation of an in-app download needs approximately twice its file size plus temporary working room. The app's memory fit estimate is approximate.
- CPU inference is available where the model/runtime supports it. GPU support depends on the device and model.
- Android 11+ uses **All files access** to manage the visible DataEater folder. Older versions use storage permissions.

## Actual verification

A Vivo/iQOO Android 16 phone has been used for development and local model checks. The interface has also been checked using narrow, landscape and tablet-sized display configurations and enlarged Android text. These are layout checks on one device, not a claim of inference validation on every phone or a physical tablet.

LFM 2.5 1.2B and Qwen 3 4B have been exercised during development. The expanded catalog's five additional bundles have verified download endpoints but have not all been inference-tested. Unsupported requested models are identified in the app instead of offering incompatible downloads.

Large technical documents and small language models can produce incomplete or incorrect answers. The public preview should be evaluated on the user's own hardware and documents.
