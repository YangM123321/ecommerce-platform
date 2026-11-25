variable "location" {
  description = "Azure region. centralus works for free subscriptions (eastus2 refused PostgreSQL)."
  type        = string
  default     = "centralus"
}

variable "suffix" {
  description = "Makes the globally-unique names unique (ACR, storage, PostgreSQL). Set once in terraform.tfvars and keep it, so names stay the same after destroy + apply."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9]{4,8}$", var.suffix))
    error_message = "suffix: 4-8 lowercase letters or digits."
  }
}

variable "node_size" {
  description = "AKS node VM size. x86 (no 'p' in the name - our images are amd64). B-series is blocked on free subscriptions."
  type        = string
  default     = "Standard_D2as_v7"
}

variable "node_count" {
  type    = number
  default = 1
}

variable "db_admin_user" {
  type    = string
  default = "ecomadmin"
}

variable "admin_email" {
  description = "Bootstrap admin account of the shop (user-service creates it on first start)."
  type        = string
  default     = "admin@ecommerce.demo"
}

variable "tags" {
  type = map(string)
  default = {
    project = "ecommerce-platform"
    managed = "terraform"
  }
}
