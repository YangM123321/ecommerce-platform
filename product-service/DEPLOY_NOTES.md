# product-service deploy notes

A file inside the service folder, so editing it triggers ONLY the product-service pipeline.
Session 6 uses it to time "merge -> new version running" without changing any code:
add a line, open a PR, merge, and time the pipeline run (docs/deploy-time.md).

- 2026-11-25: pipeline created
