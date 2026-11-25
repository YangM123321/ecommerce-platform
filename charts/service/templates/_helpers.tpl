{{- define "service.labels" -}}
app: {{ required "name is required" .Values.name }}
app.kubernetes.io/name: {{ .Values.name }}
app.kubernetes.io/part-of: ecommerce-platform
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end }}
