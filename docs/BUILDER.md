# Database builder moved

The standalone builder project is [DataEater_builder](../../DataEater_builder/README.md). It has its own environment, tests and documentation.

Use its `review` command to export PDF text and the LLM prompt, then `build-reviewed` to create a database while preserving original page references.

The legacy `tools/dataeater-builder` launcher in this Android project forwards to the sibling builder.
