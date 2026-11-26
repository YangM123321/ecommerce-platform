# Created by scripts/azure/bootstrap-state.ps1 - where Terraform keeps its state.
resource_group_name  = "rg-ecommerce-tfstate"
storage_account_name = "sttfecomtxgqz2"
container_name       = "tfstate"
key                  = "ecommerce.tfstate"
