# Everything the app needs on Azure. "terraform destroy" removes all of it (state is kept elsewhere).
# Session 5 did the same with az CLI commands in setup-azure.ps1; this file replaces that script.

locals {
  name = "ecommerce"
}

resource "azurerm_resource_group" "main" {
  name     = "rg-${local.name}"
  location = var.location
  tags     = var.tags
}

# ---------- Container registry: the 6 Docker images ----------
resource "azurerm_container_registry" "acr" {
  name                = "acrecom${var.suffix}"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  sku                 = "Basic"
  admin_enabled       = false # nobody logs in with a shared password; AKS uses its identity
  tags                = var.tags
}

# ---------- Blob Storage: product images ----------
resource "azurerm_storage_account" "images" {
  name                            = "stecom${var.suffix}"
  resource_group_name             = azurerm_resource_group.main.name
  location                        = azurerm_resource_group.main.location
  account_tier                    = "Standard"
  account_replication_type        = "LRS"
  min_tls_version                 = "TLS1_2"
  allow_nested_items_to_be_public = true # lets the container allow anonymous READ of single images
  tags                            = var.tags
}

resource "azurerm_storage_container" "product_images" {
  name                  = "product-images"
  storage_account_id    = azurerm_storage_account.images.id
  container_access_type = "blob" # read one blob by URL; no listing, no writing without the key
}

# ---------- PostgreSQL Flexible Server + one database per service ----------
resource "random_password" "db" {
  length           = 28
  special          = true
  override_special = "-_"
  min_upper        = 2
  min_lower        = 2
  min_numeric      = 2
}

resource "azurerm_postgresql_flexible_server" "db" {
  name                          = "pg-ecom-${var.suffix}"
  resource_group_name           = azurerm_resource_group.main.name
  location                      = azurerm_resource_group.main.location
  version                       = "16"
  sku_name                      = "B_Standard_B1ms"
  storage_mb                    = 32768
  backup_retention_days         = 7
  administrator_login           = var.db_admin_user
  administrator_password        = random_password.db.result
  public_network_access_enabled = true
  tags                          = var.tags

  lifecycle {
    # Azure picks an availability zone; don't try to "fix" it on the next apply.
    ignore_changes = [zone, high_availability[0].standby_availability_zone]
  }
}

# 0.0.0.0 - 0.0.0.0 is Azure's special rule for "allow Azure services" (so AKS can connect).
# Stretch goal: private networking (VNet integration) instead.
resource "azurerm_postgresql_flexible_server_firewall_rule" "azure_services" {
  name             = "AllowAzureServices"
  server_id        = azurerm_postgresql_flexible_server.db.id
  start_ip_address = "0.0.0.0"
  end_ip_address   = "0.0.0.0"
}

resource "azurerm_postgresql_flexible_server_database" "service_dbs" {
  for_each  = toset(["users_db", "catalog_db", "orders_db"])
  name      = each.key
  server_id = azurerm_postgresql_flexible_server.db.id
  charset   = "UTF8"
  collation = "en_US.utf8"
}

# ---------- AKS: runs the containers ----------
resource "azurerm_kubernetes_cluster" "aks" {
  name                = "aks-${local.name}"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  dns_prefix          = "aks-${local.name}-${var.suffix}"
  sku_tier            = "Free"
  tags                = var.tags

  default_node_pool {
    name       = "system"
    vm_size    = var.node_size
    node_count = var.node_count

    upgrade_settings {
      max_surge = "10%"
    }
  }

  identity {
    type = "SystemAssigned"
  }

  # Managed NGINX ingress controller ("application routing" add-on) with a public IP.
  web_app_routing {
    dns_zone_ids = []
  }
}

# Let the cluster's kubelet pull images from our registry - no registry password anywhere.
resource "azurerm_role_assignment" "aks_pull_acr" {
  scope                            = azurerm_container_registry.acr.id
  role_definition_name             = "AcrPull"
  principal_id                     = azurerm_kubernetes_cluster.aks.kubelet_identity[0].object_id
  skip_service_principal_aad_check = true
}

# ---------- App secrets (generated, never typed or committed) ----------
resource "random_password" "jwt" {
  length  = 48
  special = false
}

resource "random_password" "admin" {
  length  = 16
  special = false
}
