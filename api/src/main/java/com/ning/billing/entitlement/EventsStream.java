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

package com.ning.billing.entitlement;

import java.util.Collection;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.entitlement.api.Entitlement.EntitlementState;
import com.ning.billing.subscription.api.SubscriptionBase;

public interface EventsStream {

    UUID getAccountId();

    DateTimeZone getAccountTimeZone();

    UUID getBundleId();

    String getBundleExternalKey();

    UUID getEntitlementId();

    EntitlementState getEntitlementState();

    LocalDate getEntitlementEffectiveEndDate();

    SubscriptionBase getSubscription();

    SubscriptionBase getBaseSubscription();

    boolean isEntitlementActive();

    boolean isBlockChange();

    boolean isEntitlementCancelled();

    boolean isSubscriptionCancelled();

    Collection<BlockingState> getCurrentSubscriptionEntitlementBlockingStatesForServices();

    Collection<BlockingState> getPendingEntitlementCancellationEvents();

    Collection<BlockingState> getSubscriptionEntitlementStates();

    Collection<BlockingState> getBundleEntitlementStates();

    Collection<BlockingState> getAccountEntitlementStates();

    Collection<BlockingState> computeAddonsBlockingStatesForNextSubscriptionBaseEvent(DateTime effectiveDate);

    Collection<BlockingState> computeAddonsBlockingStatesForFutureSubscriptionBaseEvents();

    InternalTenantContext getInternalTenantContext();
}