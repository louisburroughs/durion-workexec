# Durion Workexec

Work Execution and Scheduling

## Overview

This component is part of the Durion ERP system built on the Moqui Framework.

## Purpose

Provide the Moqui-side entities, services, and screens for work execution workflows, aligned to the `workexec` domain business rules for receiving, customer approvals, estimate revisions, and mechanic picking with immutable audit trails.

## Scope

In scope:
- Receiving workflows (staging vs quarantine) and related operational screens
- Capturing customer approvals (digital/in-person/partial) with tamper-evident/auditable records
- Draft estimate management and revision history prior to approval
- Mechanic picking workflows (scan/partial pick/confirm) and exception reporting (e.g., not found)

Out of scope:
- Default location configuration (owned by Location domain)
- Authorization policy enforcement (owned by Security; consumed here)
- Inventory valuation and cost logic (owned by Inventory/Pricing/Accounting depending on concern)

## Structure

- `data/` - Seed and demo data
- `entity/` - Entity definitions
- `screen/` - UI screens and forms
- `service/` - Service definitions
- `src/` - Groovy/Java source code
- `template/` - Email and document templates
- `test/` - Test specifications

## Dependencies

See `component.xml` for component dependencies.

## Installation

This component is managed via `myaddons.xml` in the Moqui project root.

## License

Same as Durion project.

---

**Last Updated:** December 09, 2025
