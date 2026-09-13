# Visitor Management System

A web application for visitor registration, approval workflows, result queries, and an extensible integration layer.

## Structure

- `visitor-h5/`: visitor-facing Vue application
- `visitor-admin/`: administration frontend
- `visitor-server/`: Spring Boot backend

## Local development

Use MySQL, object storage, and integration adapters configured through environment variables. Copy the public configuration template before starting and provide local-only credentials outside Git. Vendor adapters should be replaced with mocks when running without external services.

The public source excludes visitor records, identity images, vendor manuals, production deployment files, SQL snapshots, logs, and credentials.

## Vendor integration

The public copy does not bundle Hikvision Artemis binaries. Build the core modules with an authorized SDK supplied in a private environment, or disable the vendor adapter for local work.
