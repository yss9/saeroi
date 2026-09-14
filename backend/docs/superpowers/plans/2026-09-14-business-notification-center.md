# Business Notification Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist counterparty business events as user notifications and show unread, navigable notifications in the shared company Header with 30-second polling.

**Architecture:** Backend command services call a transaction-bound `NotificationService` after each successful domain transition. A dedicated notification query API exposes recipient-scoped pages and idempotent read operations. The frontend owns notification server state through TanStack Query and renders a focused `NotificationCenter` inside `CompanyDashboardLayout`.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, MySQL 8.4, JUnit 5, MockMvc, React 19, TypeScript 6, TanStack Query 5, Axios, Vitest, Testing Library.

**Spec:** `docs/superpowers/specs/2026-09-14-business-notification-center-design.md`

## Global Constraints

- Notify only the counterparty; never create a notification for the actor who performed the action.
- Create the notification in the same database transaction as the business transition.
- Deduplicate every event with a unique `idempotencyKey`.
- Expose only the authenticated user's notifications and return 404 for another user's notification id.
- Poll on initial render, every 30 seconds, and when the browser tab regains focus.
- Show notifications only for authenticated BRAND, PROCESSOR, and SUPPLIER users.
- Keep read notifications; no delete, preference, email, push, SSE, or WebSocket behavior.
- Product-order notification types remain defined but are not emitted until product-order command APIs exist.

---

### Task 1: Notification persistence and schema

**Files:**
- Create: `src/main/java/com/ssafy/upcycle/notification/entity/NotificationType.java`
- Create: `src/main/java/com/ssafy/upcycle/notification/entity/NotificationReferenceType.java`
- Create: `src/main/java/com/ssafy/upcycle/notification/entity/UserNotification.java`
- Create: `src/main/java/com/ssafy/upcycle/notification/repository/UserNotificationRepository.java`
- Create: `src/test/java/com/ssafy/upcycle/notification/UserNotificationTests.java`
- Modify: `src/main/resources/schema.sql`
- Create: `src/main/resources/db/migrations/2026-09-14-user-notification.sql`

**Interfaces:**
- Produces: `UserNotification.create(Long, NotificationType, String, String, String, NotificationReferenceType, Long, String, LocalDateTime)`.
- Produces: `UserNotification.markRead(LocalDateTime)` as an idempotent operation.
- Produces: repository recipient queries and unread count/update methods used by Task 2.

- [ ] **Step 1: Write failing entity tests**

```java
@Test
void notificationValidatesAndMarksReadIdempotently() {
    LocalDateTime now = LocalDateTime.of(2026, 9, 14, 10, 0);
    UserNotification notification = UserNotification.create(
            7L, NotificationType.PROCESSING_REQUEST_RECEIVED,
            "새 가공 요청이 도착했어요", "300g 가공 요청을 확인해 주세요.",
            "/processor/jobs", NotificationReferenceType.PROCESSING_REQUEST,
            41L, "PROCESSING_REQUEST_RECEIVED:41:7", now);

    notification.markRead(now.plusMinutes(1));
    notification.markRead(now.plusMinutes(2));

    assertThat(notification.getReadAt()).isEqualTo(now.plusMinutes(1));
    assertThatThrownBy(() -> UserNotification.create(
            7L, NotificationType.PROCESSING_REQUEST_RECEIVED, "", "message",
            "https://outside.example", NotificationReferenceType.PROCESSING_REQUEST,
            41L, "key", now)).isInstanceOf(IllegalArgumentException.class);
}
```

- [ ] **Step 2: Run the entity test and verify RED**

Run:

```bat
mvnw.cmd -Dtest=UserNotificationTests test
```

Expected: compilation fails because the notification types and entity do not exist.

- [ ] **Step 3: Implement the entity and repository**

Define notification enum values for processing (`PROCESSING_REQUEST_RECEIVED`, `PROCESSING_QUOTE_RECEIVED`, `PROCESSING_QUOTE_SELECTED`, `PROCESSING_PAYMENT_COMPLETED`, `PROCESSING_MATERIAL_SHIPPED`, `PROCESSING_MATERIAL_RECEIVED`, `PROCESSING_STARTED`, `PROCESSING_RESULT_SUBMITTED`, `PROCESSING_RESULT_REJECTED`, `PROCESSING_RESULT_APPROVED`, `PROCESSING_REQUEST_CANCELED`, `PROCESSING_REQUEST_REJECTED`, `PROCESSING_REQUEST_EXPIRED`, `PROCESSING_JOB_EXPIRED`), trade (`TRADE_CREATED`, `TRADE_PAID`, `TRADE_CANCELED`, `TRADE_SHIPPED`, `TRADE_RECEIVED`), and product (`PRODUCT_ORDER_CREATED`, `PRODUCT_ORDER_PAID`, `PRODUCT_ORDER_CANCELED`, `PRODUCT_ORDER_SHIPPED`).

Repository contract:

```java
Page<UserNotification> findAllByRecipientIdOrderByCreatedAtDescIdDesc(
        Long recipientId, Pageable pageable);
long countByRecipientIdAndReadAtIsNull(Long recipientId);
Optional<UserNotification> findByIdAndRecipientId(Long id, Long recipientId);
Optional<UserNotification> findByIdempotencyKey(String idempotencyKey);

@Modifying
@Query("update UserNotification n set n.readAt = :readAt "
        + "where n.recipientId = :recipientId and n.readAt is null")
int markAllRead(Long recipientId, LocalDateTime readAt);
```

- [ ] **Step 4: Add the schema and migration**

Create `user_notification` with foreign key `recipient_id -> users.id`, unique `idempotency_key`, `VARCHAR(60)` enums, `VARCHAR(200)` title, `VARCHAR(500)` message, `VARCHAR(500)` target path, nullable `read_at`, and the indexes specified by the design. The migration must contain the same `CREATE TABLE` statement and must not alter or delete existing data.

- [ ] **Step 5: Run the test and verify GREEN**

```bat
mvnw.cmd -Dtest=UserNotificationTests test
```

Expected: all `UserNotificationTests` pass.

- [ ] **Step 6: Commit**

```bat
git add src/main/java/com/ssafy/upcycle/notification src/main/resources/schema.sql src/main/resources/db/migrations/2026-09-14-user-notification.sql src/test/java/com/ssafy/upcycle/notification/UserNotificationTests.java
git commit -m "feat(notification): 사용자 알림 저장 구조 추가"
```

### Task 2: Recipient-scoped notification API

**Files:**
- Create: `src/main/java/com/ssafy/upcycle/notification/NotificationErrorCode.java`
- Create: `src/main/java/com/ssafy/upcycle/notification/dto/NotificationResponse.java`
- Create: `src/main/java/com/ssafy/upcycle/notification/dto/NotificationPageResponse.java`
- Create: `src/main/java/com/ssafy/upcycle/notification/dto/NotificationReadAllResponse.java`
- Create: `src/main/java/com/ssafy/upcycle/notification/service/NotificationService.java`
- Create: `src/main/java/com/ssafy/upcycle/notification/controller/NotificationController.java`
- Create: `src/test/java/com/ssafy/upcycle/notification/NotificationApiTests.java`

**Interfaces:**
- Consumes: Task 1 repository and entity.
- Produces: `NotificationService.notify(NotificationCommand)` for domain services.
- Produces: `GET /notifications`, `PATCH /notifications/{notificationId}/read`, and `PATCH /notifications/read-all`.

- [ ] **Step 1: Write failing API tests**

```java
@Test
void listReturnsOnlyMyNotificationsAndUnreadCount() throws Exception {
    saveNotification(userId, "mine-unread", null);
    saveNotification(userId, "mine-read", now.minusMinutes(1));
    saveNotification(otherUserId, "other", null);

    mvc.perform(get("/notifications?page=0&size=20")
            .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("data.items.length()").value(2))
        .andExpect(jsonPath("data.unreadCount").value(1));
}

@Test
void cannotReadAnotherUsersNotification() throws Exception {
    long id = saveNotification(otherUserId, "other", null).getId();
    mvc.perform(patch("/notifications/{id}/read", id)
            .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
        .andExpect(status().isNotFound());
}
```

- [ ] **Step 2: Run API tests and verify RED**

```bat
mvnw.cmd -Dtest=NotificationApiTests test
```

Expected: 404 because `NotificationController` does not exist.

- [ ] **Step 3: Implement command and query behavior**

Use this immutable command boundary:

```java
public record NotificationCommand(
        Long recipientId,
        NotificationType type,
        String title,
        String message,
        String targetPath,
        NotificationReferenceType referenceType,
        Long referenceId,
        String idempotencyKey) {}
```

`notify` must skip a null recipient, reject invalid data through the entity factory, and return the existing row when `idempotencyKey` already exists. List size is clamped to `1..50`; default page is 0 and size is 20. Read operations use `LocalDateTime.now(clock)`.

- [ ] **Step 4: Implement controller endpoints**

```java
@GetMapping
ApiResponse<NotificationPageResponse> list(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size)

@PatchMapping("/{notificationId}/read")
ApiResponse<NotificationResponse> read(
        @AuthenticationPrincipal Jwt jwt, @PathVariable Long notificationId)

@PatchMapping("/read-all")
ApiResponse<NotificationReadAllResponse> readAll(@AuthenticationPrincipal Jwt jwt)
```

- [ ] **Step 5: Run API tests and verify GREEN**

```bat
mvnw.cmd -Dtest=NotificationApiTests test
```

Expected: recipient isolation, paging, unread count, single read, repeated read, and read-all tests pass.

- [ ] **Step 6: Commit**

```bat
git add src/main/java/com/ssafy/upcycle/notification src/test/java/com/ssafy/upcycle/notification/NotificationApiTests.java
git commit -m "feat(notification): 알림 조회와 읽음 API 구현"
```

### Task 3: Processing notifications

**Files:**
- Modify: `src/main/java/com/ssafy/upcycle/processing/service/ProcessingRequestService.java`
- Modify: `src/main/java/com/ssafy/upcycle/processing/service/ProcessingQuoteService.java`
- Modify: `src/main/java/com/ssafy/upcycle/processing/service/ProcessingJobService.java`
- Modify: `src/main/java/com/ssafy/upcycle/processing/service/ProcessingPaymentService.java`
- Modify: `src/main/java/com/ssafy/upcycle/processing/service/ProcessingJobWorkflowService.java`
- Modify: `src/main/java/com/ssafy/upcycle/processing/service/ProcessingLifecycleService.java`
- Modify: `src/main/java/com/ssafy/upcycle/processing/service/ProcessingResultService.java`
- Modify: `src/test/java/com/ssafy/upcycle/processing/ProcessingRequestTests.java`

**Interfaces:**
- Consumes: `NotificationService.notify(NotificationCommand)` from Task 2.
- Produces: one counterparty notification for every successful processing transition across both current and compatibility endpoints.

- [ ] **Step 1: Add failing processing integration assertions**

After each existing transition request, query `user_notification` and assert recipient, type, path, and count. The first assertion is:

```java
assertThat(jdbc.queryForMap("""
        select recipient_id, type, target_path
        from user_notification
        where reference_type = 'PROCESSING_REQUEST' and reference_id = ?
        """, requestId))
    .containsEntry("RECIPIENT_ID", processorId)
    .containsEntry("TYPE", "PROCESSING_REQUEST_RECEIVED")
    .containsEntry("TARGET_PATH", "/processor/jobs");
```

Add a rollback case that triggers an invalid transition and asserts the notification count remains unchanged.

- [ ] **Step 2: Run processing tests and verify RED**

```bat
mvnw.cmd -Dtest=ProcessingRequestTests test
```

Expected: assertions fail because no notification rows exist.

- [ ] **Step 3: Emit request, quote, selection, payment, and terminal notifications**

Call `notify` only after the corresponding entity transition succeeds. Build idempotency keys as `<TYPE>:<referenceId>:<recipientId>`, for example `PROCESSING_QUOTE_RECEIVED:55:7`. Use `/brand/processing/requests/{requestId}` for Brand quote notifications, `/processor/jobs` for a new request, and `/processing/jobs/{jobId}` for job transitions.

- [ ] **Step 4: Emit workflow notifications without duplication**

Both final and compatibility services exist. They must use the same notification type and idempotency key for the same transition, so retries or alternative endpoint paths still create one notification. Result drafts do not notify; only submitted results notify the Brand.

- [ ] **Step 5: Run processing tests and verify GREEN**

```bat
mvnw.cmd -Dtest=ProcessingRequestTests,ProcessingJobDomainTests test
```

Expected: processing tests pass and each transition has exactly one counterparty notification.

- [ ] **Step 6: Commit**

```bat
git add src/main/java/com/ssafy/upcycle/processing/service src/test/java/com/ssafy/upcycle/processing/ProcessingRequestTests.java
git commit -m "feat(notification): 가공 업무 알림 연결"
```

### Task 4: Material trade notifications

**Files:**
- Modify: `src/main/java/com/ssafy/upcycle/trade/service/TradeCommandService.java`
- Modify: `src/main/java/com/ssafy/upcycle/trade/service/PaymentService.java`
- Modify: `src/main/java/com/ssafy/upcycle/trade/service/TradeShipmentService.java`
- Modify: `src/main/java/com/ssafy/upcycle/trade/service/TradeReceiptService.java`
- Modify: corresponding existing tests under `src/test/java/com/ssafy/upcycle/trade/`

**Interfaces:**
- Consumes: `NotificationService.notify(NotificationCommand)` from Task 2.
- Produces: trade-created, paid, canceled, shipped, and received notifications targeting `/trades/{tradeId}`.

- [ ] **Step 1: Write failing trade notification assertions**

```java
assertThat(notifications.findAllByRecipientIdOrderByCreatedAtDescIdDesc(
        sellerId, PageRequest.of(0, 20)).getContent())
    .anySatisfy(notification -> {
        assertThat(notification.getType()).isEqualTo(NotificationType.TRADE_CREATED);
        assertThat(notification.getTargetPath()).isEqualTo("/trades/" + tradeId);
    });
```

Cover buyer cancellation -> Supplier, Supplier cancellation -> Brand, Supplier shipment -> Brand, and Brand receipt -> Supplier.

- [ ] **Step 2: Run the affected trade tests and verify RED**

```bat
mvnw.cmd -Dtest=ConcurrentPurchaseTests,MarketplaceApiTests,TradeShipmentServiceTests,TradeReceiptServiceTests test
```

Expected: notification assertions fail with empty results.

- [ ] **Step 3: Add notifications after successful transitions**

Resolve the counterparty from `MaterialTrade.sellerId` and `buyerId`. Use `TRADE_<EVENT>:<tradeId>:<recipientId>` keys and `/trades/{tradeId}` paths. Cancellation sends to the party other than `actorId`; system cancellation sends to both parties only when both are non-null and different.

- [ ] **Step 4: Run trade tests and verify GREEN**

```bat
mvnw.cmd -Dtest=ConcurrentPurchaseTests,MarketplaceApiTests,TradeShipmentServiceTests,TradeReceiptServiceTests test
```

Expected: all selected tests pass with one notification per event.

- [ ] **Step 5: Commit**

```bat
git add src/main/java/com/ssafy/upcycle/trade/service src/test/java/com/ssafy/upcycle/trade
git commit -m "feat(notification): 소재 거래 알림 연결"
```

### Task 5: Frontend notification data layer

**Files:**
- Create: `src/features/notification/types/notification.types.ts`
- Create: `src/features/notification/api/notificationApi.ts`
- Create: `src/features/notification/api/notificationApi.test.ts`
- Create: `src/features/notification/hooks/useNotifications.ts`
- Create: `src/features/notification/hooks/useNotifications.test.tsx`

**Interfaces:**
- Consumes: Task 2 REST API.
- Produces: `useNotifications(enabled)`, `useReadNotification()`, and `useReadAllNotifications()`.

- [ ] **Step 1: Create the frontend feature branch**

From `S15P21E104_front`:

```bat
git fetch origin frontend/develop
git switch -c frontend/feat/business-notifications origin/frontend/develop
```

- [ ] **Step 2: Write failing API and hook tests**

```tsx
it('polls notifications every 30 seconds and on window focus', () => {
  const options = notificationQueryOptions(true)
  expect(options.refetchInterval).toBe(30_000)
  expect(options.refetchOnWindowFocus).toBe(true)
  expect(options.enabled).toBe(true)
})
```

API tests assert `GET /notifications?page=0&size=20`, `PATCH /notifications/12/read`, and `PATCH /notifications/read-all` through the shared `apiClient`.

- [ ] **Step 3: Run tests and verify RED**

```bat
npm test -- src/features/notification/api/notificationApi.test.ts src/features/notification/hooks/useNotifications.test.tsx
```

Expected: imports fail because the notification feature does not exist.

- [ ] **Step 4: Implement types, API, keys, and hooks**

```ts
export const notificationKeys = {
  all: ['notifications'] as const,
  list: (page = 0, size = 20) => [...notificationKeys.all, page, size] as const,
}

export function notificationQueryOptions(enabled: boolean) {
  return {
    queryKey: notificationKeys.list(),
    queryFn: () => notificationApi.list(0, 20),
    enabled,
    refetchInterval: 30_000,
    refetchOnWindowFocus: true,
  }
}
```

Read mutations invalidate `notificationKeys.all`; optimistic badge clearing is allowed only for `readAll` and must roll back on error.

- [ ] **Step 5: Run tests and verify GREEN**

```bat
npm test -- src/features/notification/api/notificationApi.test.ts src/features/notification/hooks/useNotifications.test.tsx
```

- [ ] **Step 6: Commit**

```bat
git add src/features/notification
git commit -m "feat(notification): 알림 API와 polling 훅 구현"
```

### Task 6: Header notification center

**Files:**
- Create: `src/features/notification/components/NotificationCenter.tsx`
- Create: `src/features/notification/components/NotificationCenter.test.tsx`
- Create: `src/features/notification/components/notification-center.css`
- Modify: `src/features/company/components/CompanyDashboardLayout.tsx`
- Modify: `src/features/company/components/CompanyDashboardLayout.test.tsx`

**Interfaces:**
- Consumes: Task 5 hooks and authenticated business-role boolean.
- Produces: accessible Header bell, unread badge, notification popover, navigation, and read actions.

- [ ] **Step 1: Write failing component tests**

```tsx
expect(await screen.findByRole('button', { name: '알림 2개' })).toBeInTheDocument()
await user.click(screen.getByRole('button', { name: '알림 2개' }))
expect(screen.getByRole('region', { name: '알림 목록' })).toBeInTheDocument()
await user.click(screen.getByRole('button', { name: '모두 읽음' }))
expect(readAll).toHaveBeenCalledOnce()
```

Add tests for `99+`, loading, empty, non-blocking error, Escape, outside click, click-to-read-and-navigate, and no query for Consumer or logged-out state.

- [ ] **Step 2: Run component tests and verify RED**

```bat
npm test -- src/features/notification/components/NotificationCenter.test.tsx src/features/company/components/CompanyDashboardLayout.test.tsx
```

Expected: the notification button cannot be found.

- [ ] **Step 3: Implement `NotificationCenter`**

Use `Bell` from `@phosphor-icons/react`. Render the badge only when `unreadCount > 0`, format counts with `Math.min(count, 99)` plus `+`, and render each notification as a button so click can await/trigger read mutation and call `navigate(targetPath)`. Close on Escape and outside pointer events.

- [ ] **Step 4: Mount and style the Header control**

Mount immediately before wallet/account actions in `CompanyDashboardLayout`. Use a 360px desktop popover, `calc(100vw - 32px)` mobile width, unread row background, visible focus rings, and `max-height` with scrolling. Preserve the existing 72px Header and responsive action layout.

- [ ] **Step 5: Run component tests and verify GREEN**

```bat
npm test -- src/features/notification/components/NotificationCenter.test.tsx src/features/company/components/CompanyDashboardLayout.test.tsx
```

- [ ] **Step 6: Commit**

```bat
git add src/features/notification src/features/company/components/CompanyDashboardLayout.tsx src/features/company/components/CompanyDashboardLayout.test.tsx
git commit -m "feat(notification): 기업 Header 알림 센터 구현"
```

### Task 7: End-to-end verification and Docker migration

**Files:**
- Modify only files required by failures found in this task.

**Interfaces:**
- Consumes: Tasks 1-6.
- Produces: tested backend and frontend branches and a running local Docker stack with preserved data.

- [ ] **Step 1: Run all backend tests**

```bat
mvnw.cmd test
```

Expected: `BUILD SUCCESS`, zero failures and zero errors.

- [ ] **Step 2: Run frontend tests, lint, and build**

```bat
npm test
npm run lint
npm run build
```

Expected: all tests pass, ESLint exits 0, TypeScript and Vite build successfully.

- [ ] **Step 3: Apply the non-destructive local migration**

From the backend directory, run the migration against the existing Compose MySQL container with its configured `MYSQL_USER`, `MYSQL_PASSWORD`, and `MYSQL_DATABASE`. Do not use `docker compose down -v`.

```powershell
Get-Content src/main/resources/db/migrations/2026-09-14-user-notification.sql |
    docker compose -f docker-compose.local.yml exec -T mysql sh -c 'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'
```

- [ ] **Step 4: Rebuild and verify health**

```bat
docker compose -f docker-compose.local.yml up -d --build backend frontend
docker compose -f docker-compose.local.yml ps
curl http://localhost:5173/api/health
```

Expected: MySQL is healthy, backend and frontend are Up, and health returns data `UP`.

- [ ] **Step 5: Perform a browser smoke test**

Log in as a Brand and Processor in separate browser profiles. Create a processing request as Brand, wait no longer than 30 seconds on the Processor page, verify the badge increments, open the notification, and verify navigation to `/processor/jobs`. Submit a quote and verify the Brand badge and link to `/brand/processing/requests/{requestId}`.

- [ ] **Step 6: Review branch state**

```bat
git status --short
git diff --check origin/backend/develop...HEAD
```

Repeat in the frontend repository against `origin/frontend/develop`. Expected: only intentional commits differ and no conflict markers or whitespace errors remain.
