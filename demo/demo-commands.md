# Demo commands

## AI use case - Batch 1

```bash
python3 discover-tree.py \
  --root /eodata/Sentinel-2/AUX/S2_GRI_L1C \
  --record-type s2-gri \
  --data-id-prefix s2-gri \
  --metadata mission=sentinel-2 \
  --group-depth 2 \
  --max-per-parent=30
```

```bash
python3 enrich-plan.py \
  --input plan.jsonl \
  --output plan-enriched.jsonl
```

```bash
INPUT=plan-enriched.jsonl ./sign-tree.sh
```

```bash
./signing-tool-linux-amd64 create-provenance-record \
  --files empty.txt \
  --provenance-record-type ml-training-dataset \
  --data-id training-dataset/s2-gri-training-set-v1 \
  --predecessors-file sentinel-2-batch.txt \
  --register-url https://124.197.43.5.nip.io/api/provenance \
  --keycloak-url https://124.197.43.5.nip.io \
  --save-record false \
  --metadata "dataset-name=s2-gri-training-set-v1,mission=sentinel-2,product-level=L1C,tile-count=$(wc -l < sentinel-2-batch.txt),curation-date=$(date -u +%Y-%m-%d)"
```

```bash
./signing-tool-linux-amd64 create-provenance-record \
  --files model.pt,model_config.json \
  --provenance-record-type ml-model \
  --data-id cloud-mask-classifier \
  --predecessors 019e6347-ea70-8867-be4d-c9123a67e094 \
  --register-url https://124.197.43.5.nip.io/api/provenance \
  --keycloak-url https://124.197.43.5.nip.io \
  --save-record false \
  --metadata "model-name=s2-cloud-mask,model-version=1.0.0,architecture=U-Net,framework=pytorch,task=cloud-segmentation,input-bands=B02-B03-B04-B08,training-date=$(date -u +%Y-%m-%d),validation-iou=0.91,license=CC-BY-4.0"
```

## AI use case - Batch 2

```bash
nohup python3 discover-tree.py \
  --root /eodata/Sentinel-2/AUX/S2_GRI_L1C \
  --record-type s2-gri \
  --data-id-prefix s2-gri \
  --metadata mission=sentinel-2 \
  --group-depth 2 \
  --skip-per-parent 30 \
  --max-per-parent=30 &
```

```bash
python3 enrich-plan.py \
  --input plan.jsonl \
  --output plan-enriched.jsonl
```

```bash
INPUT=plan-enriched.jsonl ./sign-tree.sh
```

```bash
./signing-tool-linux-amd64 create-provenance-record \
  --files empty.txt \
  --provenance-record-type ml-training-dataset \
  --data-id training-dataset/s2-gri-training-set-v2 \
  --predecessors-file sentinel-2-batch-2.txt \
  --register-url https://124.197.43.5.nip.io/api/provenance \
  --keycloak-url https://124.197.43.5.nip.io \
  --save-record false \
  --metadata "dataset-name=s2-gri-training-set-v2,mission=sentinel-2,product-level=L1C,tile-count=$(wc -l < sentinel-2-batch-2.txt),curation-date=$(date -u +%Y-%m-%d)"
```

```bash
./signing-tool-linux-amd64 create-provenance-record \
  --files model.pt,model_config.json \
  --provenance-record-type ml-model \
  --data-id cloud-mask-classifier \
  --predecessors 019e6366-1518-81e7-aa73-811b54c25307,019e637d-b3f8-8099-9c03-3b249575887f \
  --register-url https://124.197.43.5.nip.io/api/provenance \
  --keycloak-url https://124.197.43.5.nip.io \
  --save-record false \
  --metadata "model-name=s2-cloud-mask,model-version=2.0.0,architecture=U-Net,framework=pytorch,task=cloud-segmentation,input-bands=B02-B03-B04-B08,training-date=$(date -u +%Y-%m-%d),validation-iou=0.91,license=CC-BY-4.0"
```

## rcrop

```bash
./signing-tool-linux-amd64 create-provenance-record \
  --files rcrop-traceforeo-master/results/aggregate_and_train_results.csv \
  --predecessors-file level-0-batch.txt \
  --provenance-record-type rcrop-aggregated-env-variables \
  --data-id rcrop-aggregated-env-variables \
  --register-url https://124.197.43.5.nip.io/api/provenance \
  --keycloak-url https://124.197.43.5.nip.io \
  --metadata metrics=MAE/RMSE/R2
```

```bash
./signing-tool-linux-amd64 create-provenance-record \
  --files rcrop-traceforeo-master/results/evaluation/cv_metrics_per_crop.csv \
  --predecessors-file level-0-batch.txt \
  --provenance-record-type rcrop-metrics-per-crop \
  --data-id rcrop-metrics-per-crop \
  --register-url https://124.197.43.5.nip.io/api/provenance \
  --keycloak-url https://124.197.43.5.nip.io \
  --metadata metrics=mean-per-crop
```

```bash
./signing-tool-linux-amd64 create-provenance-record \
  --files rcrop-traceforeo-master/results/tables/predictions_2024_by_nuts.csv \
  --predecessors 019e4a36-86e0-8f0b-8881-34ef5c9c85a1 \
  --provenance-record-type rcrop-predictions-2024-all \
  --data-id rcrop-predictions-2024-all \
  --register-url https://124.197.43.5.nip.io/api/provenance \
  --keycloak-url https://124.197.43.5.nip.io
```

```bash
./signing-tool-linux-amd64 create-provenance-record \
  --directory rcrop-traceforeo-master/results/tables/ \
  --predecessors 019e4a36-05f8-894e-946c-cc282a674ee0 \
  --provenance-record-type rcrop-predictions-2024-per-crop \
  --data-id rcrop-2024-predictions-per-crop \
  --register-url https://124.197.43.5.nip.io/api/provenance \
  --keycloak-url https://124.197.43.5.nip.io \
  --metadata predictions=per-crop-predictions
```

```bash
./signing-tool-linux-amd64 create-provenance-record \
  --directory rcrop-traceforeo-master/results/polygons/ \
  --predecessors 019e4a37-2af0-8c1a-988b-f98c36981e66,019e4a37-c348-88dc-b750-6ec0e2313624 \
  --provenance-record-type rcrop-geospatial-outputs \
  --data-id rcrop-geospatial-outputs \
  --register-url https://124.197.43.5.nip.io/api/provenance \
  --keycloak-url https://124.197.43.5.nip.io \
  --metadata geospatial=NUTS2-geometries
```

```bash
./signing-tool-linux-amd64 create-provenance-record \
  --directory rcrop-traceforeo-master/results/maps/predictions/ \
  --predecessors 019e4a38-6758-88bf-bc6c-6f7a7273a121 \
  --provenance-record-type rcrop-map-renders-predicted-yields \
  --data-id rcrop-map-renders-predicted-yields \
  --register-url https://124.197.43.5.nip.io/api/provenance \
  --keycloak-url https://124.197.43.5.nip.io \
  --metadata renders=yields
```
