# Deploy time: manual vs pipeline (Session 6)

Resume target: "deploy time cut by 50%". The number used on the resume is whatever these
measurements show.

**What is measured**
- **Manual** (the process in [manual-deploy.md](manual-deploy.md)): run all tests, build and push
  all 6 images, deploy all 6 with Helm, run the smoke tests. Started from my PC with
  `scripts\measure\manual-deploy-timed.ps1`, which prints one row.
- **Pipeline**: a one-line change in `product-service/DEPLOY_NOTES.md`, PR merged, then the time from
  the merge until the Deploy stage's smoke test passed. Read it in Azure DevOps (run summary:
  start and finish times). **Approve immediately**, and write down the approval wait separately so it
  can be left out.

Both use the same infrastructure (1 AKS node) on the same day.

## Manual (3 runs)

| Date | Commit | Tests | Build + push + deploy | Smoke | **Total (mm:ss)** |
|---|---|---|---|---|---|
| | | | | | |
| | | | | | |
| | | | | | |
| | | | | | **Average:** |

## Pipeline (3 runs)

| Date | PR | Merge time | Smoke passed | Approval wait | **Merge to running, without approval wait (mm:ss)** |
|---|---|---|---|---|---|
| | | | | | |
| | | | | | |
| | | | | | **Average:** |

## Result

Deploy-time reduction = (manual average - pipeline average) / manual average = **___ %**

Also worth saying in an interview:
- **Hands-on time:** manual = the whole time (someone runs and watches every step). Pipeline = one
  approval click.
- **Why faster:** only the changed service is rebuilt and redeployed; tests and analysis run on
  Microsoft's machines; nothing is forgotten.
- **What the pipeline adds that manual didn't have:** SonarCloud quality gate, published test and
  coverage reports, every image traceable to a commit SHA, an approval record.
