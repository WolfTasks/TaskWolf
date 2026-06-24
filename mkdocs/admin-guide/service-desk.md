# Service Desk

The service desk module provides ITSM-light functionality: ticket queues, SLA policies, incident tracking, and email-to-ticket ingestion.

## Setting Up a Service Desk

1. Go to **Admin → Service Desks → New Service Desk**
2. Assign it to a project — tickets become issues in that project
3. Configure SLA policies (see below)

## SLA Policies

An SLA policy defines response time targets and escalation rules.

1. Open the service desk → **SLA Policies → Add Policy**
2. Set: Name, Priority filter, Response target (minutes), Resolution target (minutes)
3. Add **Escalation Rules** — notify a user or role when the SLA is about to breach

The SLA monitor checks for breaches every minute.

## Incidents

Incidents track major outages or service disruptions.

- Create from **Service Desk → Incidents → New Incident**
- Set **Severity** (P1–P4) and affected service
- When an incident is resolved, a **Postmortem** is created automatically

### Postmortem

The postmortem form captures: timeline, root cause, contributing factors, action items, and lessons learned. It is linked to the incident and visible in the incident detail view.

## Email-to-Ticket (IMAP)

Configure IMAP credentials in your `.env` file (see [Configuration](../configuration.md)). Emails received in the configured inbox are converted to tickets automatically.

Requires `TW_IMAP_HOST`, `TW_IMAP_USER`, and `TW_IMAP_PASS` to be set.
