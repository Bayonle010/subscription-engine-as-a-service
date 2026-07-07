# Subscription Engine as a Service

A multi-tenant recurring billing engine built on top of Nomba payment primitives.  
It provides subscription checkout, tokenized card billing, renewal processing, dunning, proration, customer self-service, reconciliation, merchant webhooks, payouts, and ledger-backed payment accounting.

---

## Overview

This project solves recurring billing for merchants who want to create products, plans, customers, subscriptions, invoices, payments, and withdrawals without building the full billing infrastructure themselves.

It supports:

- Multi-tenant merchant accounts
- Product and plan management
- Subscription checkout
- Card tokenization through Nomba Checkout
- Automatic renewal billing
- Transaction verification before delivering value
- Dunning and failed payment recovery
- Customer portal for self-service actions
- Proration and plan switching
- Reconciliation for missed/uncertain payment states
- Merchant webhook delivery
- Merchant payout accounts and withdrawals
- Ledger posting for reliable financial accounting

---

## Tech Stack

- Java 25
- Spring Boot 4
- Spring Security
- Spring Data JPA / Hibernate
- PostgreSQL
- Docker
- Nomba APIs
- ZeptoMail for email notifications
- REST API architecture

---

## Core Modules

### Authentication and Multi-Tenancy

The system supports both dashboard users and API clients.

Merchant users authenticate using JWT, while API clients authenticate using generated API credentials. Each request is scoped to a tenant/account to keep merchant data isolated.

Supported merchant roles include:

- `OWNER`
- `ADMIN`
- `DEVELOPER`
- `SUPPORT`
- `API_CLIENT`

---

## Product and Plan Management

Merchants can create products and attach billing plans to them.

A plan defines:

- Amount
- Currency
- Billing interval
- Billing interval count
- Trial days
- Features
- Plan status

Supported billing intervals include:

- Daily
- Weekly
- Monthly
- Yearly
- Custom

---

## Subscription Checkout

A merchant can create a subscription checkout session for a customer.

The checkout flow uses Nomba Checkout with card tokenization enabled.

The first payment is customer-initiated. The customer completes payment on Nomba’s hosted checkout page and enters OTP/3DS if required by the bank.

After successful payment, Nomba sends a webhook containing tokenized card data. The system stores the `tokenKey` and activates the subscription.

The subscription is only activated after successful payment verification.

---

## Tokenized Card Renewals

Renewals use the saved Nomba `tokenKey`.

The renewal processor charges the customer using Nomba’s tokenized card payment endpoint.

Important safety rule:

> A tokenized charge response alone is not trusted as final payment success.

After calling the tokenized card endpoint, the system verifies the transaction using the renewal `orderReference`.

Only after verification confirms successful payment does the system:

- Mark the invoice as paid
- Create a successful payment record
- Post ledger entries
- Extend the subscription period
- Emit `invoice.paid` and `payment.succeeded` events


---

## Renewal Billing Flow

The renewal scheduler finds active subscriptions whose current billing period has ended.

Renewal flow:

1. Find due active subscriptions
2. Lock subscription for renewal processing
3. Apply any scheduled plan switch
4. Check if subscription is due
5. If cancellation is scheduled, cancel at period end
6. Create or reuse renewal invoice
7. Check for an existing processing payment attempt
8. Charge saved tokenized card
9. Verify transaction using Nomba order reference
10. If successful, renew subscription
11. If failed, mark past due and open dunning

The system avoids duplicate charges by checking for existing processing attempts before charging again.

---

## Dunning and Failed Payment Recovery

Dunning handles failed renewal payments.

When a renewal payment fails:

- The payment attempt is marked `FAILED`
- The invoice remains `OPEN`
- The subscription is marked `PAST_DUE`
- A dunning case is opened
- A `payment.failed` event is recorded
- A payment failure email is sent to the customer

Dunning tracks:

- Failed renewal reason
- Retry attempts
- Next retry time
- Grace period end
- Subscription and invoice involved

The dunning flow prevents failed payments from causing tight retry loops. Instead of retrying every scheduler tick, the system uses controlled retry timing and grace periods.

Payment recovery should be completed through a customer-facing checkout/payment rescue flow so the customer can manually complete payment or update their payment method.

---

## Customer Portal

The customer portal gives customers a self-service interface for subscription management.

Customers can use the portal to:

- View subscription details
- View invoices and payment history
- Request payment method update
- Complete payment recovery checkout
- Cancel subscription at period end
- Manage subscription-related actions without merchant support

When a payment method update is requested, automatic renewal is skipped and a renewal checkout session is created so the customer can complete payment with a new card.

---

## Proration and Plan Switching

The system supports plan switching with proration.

When a customer changes plan before the end of the current billing period, the engine calculates the unused value of the current plan and applies it against the new plan.

Proration handles:

- Upgrades
- Downgrades
- Immediate plan switches
- Scheduled plan switches
- Remaining time in the current period
- Difference between current and new plan amount

Scheduled plan switches are applied during renewal processing before the next renewal charge is attempted.

---

## Reconciliation

Reconciliation protects the system from missed webhooks, delayed provider responses, or uncertain payment states.

The reconciliation flow rechecks transactions from Nomba using saved references such as `orderReference`.

It is used when:

- A webhook is missed
- A transaction is stuck in processing
- A payment attempt requires verification
- Provider response is unclear
- Renewal checkout needs confirmation
- Payout or transfer status needs requery

Reconciliation ensures the system does not deliver value until provider-side payment status is confirmed.

---

## Webhooks

The platform processes Nomba webhooks and also sends merchant webhooks.

### Inbound Nomba Webhooks

Inbound webhook events include payment success, payment failure, and payout events.

The webhook processor verifies the event, extracts the payload, updates internal records, and triggers the correct workflow.

Examples:

- Activate subscription after first checkout payment
- Complete renewal checkout
- Mark failed checkout
- Update payout status
- Trigger reconciliation where needed

### Merchant Webhooks

Merchants can configure webhook endpoints to receive billing events from the platform.

Supported event types include:

- `subscription.activated`
- `subscription.cancelled`
- `invoice.paid`
- `payment.succeeded`
- `payment.failed`

Webhook delivery is handled through an outbox processor with retry support.

If a merchant endpoint fails, the event is retried later instead of being lost.

---

## Event Outbox

The system uses an outbox pattern for reliable event delivery.

Instead of sending emails or merchant webhooks directly inside the main transaction, the system records an outbox event first.

The outbox processor later delivers:

- Customer emails
- Merchant webhook notifications
- Billing event notifications

This improves reliability and prevents important events from being lost if an external service is temporarily unavailable.

---

## Email Notifications

Customer emails are sent through ZeptoMail.

Email notifications include:

- Payment successful
- Payment failed
- Subscription activated
- Subscription cancelled
- Renewal/payment recovery messages

Payment failure emails are triggered from the outbox after a failed renewal event is recorded.

---

## Invoices and Payments

Each renewal creates or reuses a renewal invoice.

Invoices track:

- Amount due
- Amount paid
- Currency
- Billing reason
- Period start
- Period end
- Status

Payments are only created after verified successful payment.

This prevents false payment records when Nomba accepts a request but later reports `PAYMENT_FAILED`.

---

## Ledger

The system uses ledger postings to track money movement.

For successful subscription payments, the ledger records:

- Gross payment amount
- Platform fee
- Merchant net amount
- Ledger transaction reference

Ledger posting happens only after payment verification succeeds.

This keeps financial records aligned with actual provider-confirmed payment status.

---

## Merchant Payout Accounts

Merchants can save verified payout accounts for withdrawals.

Payout account flow:

1. Sync banks from Nomba
2. Lookup bank account
3. Verify account name and bank code
4. Save payout account
5. Use payout account for withdrawals

Payout account types include:

- Bank account
- Nomba wallet

Disabled payout accounts cannot be used for withdrawals.

---

## Merchant Withdrawals

Merchants can request withdrawals from their available balance.

Withdrawal flow:

1. Merchant selects a saved payout account
2. System validates balance and payout account
3. Funds are moved from merchant payable balance into payout settlement
4. Nomba transfer is initiated
5. Transfer result is stored
6. Webhook or reconciliation confirms final status
7. Ledger is settled or reversed

Withdrawal statuses include:

- `HELD`
- `DISPATCHING`
- `PROCESSING`
- `SUCCEEDED`
- `FAILED`
- `REVERSED`
- `MANUAL_REVIEW`

The withdrawal system prevents double spending by holding merchant funds before initiating the external transfer.

---

## Tokenized Card Limitation

Tokenized cards can support recurring billing, but the system must still handle cases where the issuing bank or provider requires OTP/customer authentication.

If OTP is required during a background renewal:

- The system cannot collect OTP inside the scheduler
- The payment is not treated as successful
- The subscription is moved into dunning/payment recovery
- The customer should complete payment through a hosted checkout/payment rescue link

This keeps the system safe and prevents access from being extended without confirmed payment.

---

## Important Safety Rules

- Do not activate a subscription until the first payment is verified
- Do not renew a subscription until renewal payment is verified
- Do not trust provider request success as payment success
- Always verify Nomba transactions using `orderReference`
- Use `data.status` from verification as the payment truth
- Do not retry failed payments in a tight loop
- Keep failed renewals in dunning with controlled retry timing
- Do not post ledger entries until payment is confirmed
- Do not send merchant webhook events directly without outbox tracking

---

## High-Level Billing State Flow

```text
Checkout Created
    ↓
Customer Pays on Nomba Hosted Checkout
    ↓
Payment Verified
    ↓
Subscription Activated
    ↓
Renewal Scheduler Runs
    ↓
Tokenized Card Charged
    ↓
Transaction Verified
    ↓
Success → Invoice Paid → Ledger Posted → Subscription Renewed
    ↓
Failure → Subscription Past Due → Dunning Opened → Payment Recovery
