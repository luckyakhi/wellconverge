# Thin wrappers so the plan/apply/outputs cycle is one command each.
# Terraform is the only way AWS resources are created here — see ADR-0005.

.PHONY: init plan apply outputs destroy fmt validate

OUTPUTS := terraform-outputs.json

init:
	terraform init

plan:
	terraform plan

apply:
	terraform apply
	$(MAKE) outputs

# Writes the resource names where wc-tools reads them.
outputs:
	terraform output -json > $(OUTPUTS)
	@echo "wrote $(OUTPUTS)"

destroy:
	terraform destroy
	@rm -f $(OUTPUTS)

fmt:
	terraform fmt -recursive

validate:
	terraform validate
