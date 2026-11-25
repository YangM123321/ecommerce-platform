# Manual deployment (baseline before CI/CD)

This is how the app gets deployed **by hand** today. Session 6 automates it with an
Azure DevOps pipeline; the times recorded here are the "before" number for the
deploy-time comparison on the resume. Fill in your own times.

## Steps (local Docker, as of Session 3)

1. Pull the latest code: `git checkout main && git pull`
2. Run all tests: `mvn verify`
3. Build the image and restart: `docker compose up --build -d`
4. Wait until healthy: `docker compose ps` shows `ecommerce-app ... (healthy)`
5. Smoke test:
   - `powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1`
   - `powershell -ExecutionPolicy Bypass -File scripts\smoke-test-orders.ps1`
6. If something fails: `docker compose logs app`, fix, and start again from step 2.

In Session 5 (Azure) these grow into: build image, tag it, push to Azure Container Registry,
`kubectl apply` the manifests for each service, watch the rollout, then smoke test the public URL.

## Timing log

Time from step 1 to a passing smoke test, with a stopwatch. Do it 3 times and average.

| Date | Run | Minutes | Notes (what was slow or went wrong) |
|---|---|---|---|
|  | 1 |  |  |
|  | 2 |  |  |
|  | 3 |  |  |
|  | **Average** |  |  |
