# Read by scripts/azure/*.ps1 with "terraform output -json". Sensitive ones are hidden in the
# console but still available to the scripts.

output "resource_group" {
  value = azurerm_resource_group.main.name
}

output "location" {
  value = azurerm_resource_group.main.location
}

output "acr_name" {
  value = azurerm_container_registry.acr.name
}

output "acr_login_server" {
  value = azurerm_container_registry.acr.login_server
}

output "aks_name" {
  value = azurerm_kubernetes_cluster.aks.name
}

output "postgres_host" {
  value = azurerm_postgresql_flexible_server.db.fqdn
}

output "db_username" {
  value = var.db_admin_user
}

output "db_password" {
  value     = random_password.db.result
  sensitive = true
}

output "jwt_secret" {
  value     = random_password.jwt.result
  sensitive = true
}

output "admin_email" {
  value = var.admin_email
}

output "admin_password" {
  value     = "Adm-${random_password.admin.result}"
  sensitive = true
}

output "storage_account" {
  value = azurerm_storage_account.images.name
}

output "storage_connection_string" {
  value     = azurerm_storage_account.images.primary_connection_string
  sensitive = true
}
