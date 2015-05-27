/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.subscription.api;

/**
 * The {@code SubscriptionBaseTransitionType}
 */
public enum SubscriptionBaseTransitionType {
    /**
     * Occurs when a {@code SubscriptionBase} got migrated to mark the start of the subscription
     */
    MIGRATE_ENTITLEMENT,
    /**
     * Occurs when a a user created a {@code SubscriptionBase} (not migrated)
     */
    CREATE,
    /**
     * Occurs when a {@code SubscriptionBase} got migrated to mark the start of the billing
     */
    MIGRATE_BILLING,
    /**
     * Occurs when a {@code SubscriptionBase} got transferred to mark the start of the subscription
     */
    TRANSFER,
    /**
     * Occurs when a user changed the current {@code Plan} of the {@code SubscriptionBase}
     */
    CHANGE,
    /**
     * Occurs when a user restarted a {@code SubscriptionBase} after it had been cancelled
     */
    RE_CREATE,
    /**
     * Occurs when a user cancelled the {@code SubscriptionBase}
     */
    CANCEL,
    /**
     * Occurs when a user uncancelled the {@code SubscriptionBase} before it reached its cancellation date
     */
    UNCANCEL,
    /**
     * Generated by the system to mark a change of phase
     */
    PHASE,
    /**
     * Generated by the system to mark the start of blocked billing overdue state. This is not on disk but computed by junction to create the billing events.
     */
    START_BILLING_DISABLED,
    /**
     * Generated by the system to mark the end of blocked billing overdue state. This is not on disk but computed by junction to create the billing events.
     */
    END_BILLING_DISABLED
}
