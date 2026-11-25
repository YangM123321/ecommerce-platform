terraform {
  required_version = ">= 1.6"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # State lives in an Azure Storage account (made once by scripts/azure/bootstrap-state.ps1),
  # so it survives "terraform destroy" and the pipeline can read it too.
  # The account name etc. come from backend.hcl: terraform init -backend-config=backend.hcl
  backend "azurerm" {}
}

provider "azurerm" {
  features {
    resource_group {
      # Let "terraform destroy" remove the group even if Azure added things to it (e.g. alerts).
      prevent_deletion_if_contains_resources = false
    }
  }
  # subscription_id comes from the ARM_SUBSCRIPTION_ID environment variable (set by the scripts).
}
