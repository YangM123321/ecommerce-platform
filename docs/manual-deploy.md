# Manual deployment (baseline before CI/CD)

This is how the app gets deployed **by hand** today. Session 6 automates it with an
Azure DevOps pipeline; the times recorded here are the "before" number for the
deploy-time comparison on the resume. Fill in your own times.

## Steps (local Docker, as of Session 4)

1. Pull the latest code: `git checkout main && git pull`
2. Run all tests: `mvn verify`
3. Build the images and restart: `docker compose up --build -d`
4. Wait until healthy: `docker compose ps` shows all 7 containers `(healthy)`
   and http://localhost:8761 lists the four registered apps
5. Smoke test:
   - `powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1`
   - `powershell -ExecutionPolicy Bypass -File scripts\smoke-test-orders.ps1`
6. If something fails: `docker compose logs <service-name>`, fix, and start again from step 2.

Deploying one service only (new in Session 4): `docker compose up --build -d order-service`.
The others keep running.

Session 5 (Azure): `scripts\azure\deploy.ps1` does it in one go: build 6 images, tag them with the
commit id, push them to Azure Container Registry, `kubectl apply` the manifests, wait for each
rollout, print the public URL. Then smoke test the public URL. Still started by hand from your PC:
Session 6 moves it into an Azure DevOps pipeline.

## Timing log

Time from step 1 to a passing smoke test, with a stopwatch. Do it 3 times and average.

| Date | Run | Minutes | Notes (what was slow or went wrong) |
|---|---|---|---|
|  | 1 |  |  |
|  | 2 |  |  |
|  | 3 |  |  |
|  | **Average** |  |  |
