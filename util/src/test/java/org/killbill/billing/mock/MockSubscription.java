/*
 * Copyright 2010-2011 Ning, Inc.
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

package org.killbill.billing.mock;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.mockito.Mockito;

import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementSourceType;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransition;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;

import com.google.common.collect.ImmutableList;

public class MockSubscription implements SubscriptionBase {

    private final UUID id;
    private final UUID bundleId;
    private final EntitlementState state;
    private Plan plan;
    private final PlanPhase phase;
    private final DateTime startDate;
    private final List<EffectiveSubscriptionInternalEvent> transitions;

    public MockSubscription(final UUID id, final UUID bundleId, final Plan plan, final DateTime startDate, final List<EffectiveSubscriptionInternalEvent> transitions) {
        this.id = id;
        this.bundleId = bundleId;
        this.state = EntitlementState.ACTIVE;
        this.plan = plan;
        this.phase = null;
        this.startDate = startDate;
        this.transitions = transitions;
    }

    public MockSubscription(final EntitlementState state, final Plan plan, final PlanPhase phase) {
        this.id = UUID.randomUUID();
        this.bundleId = UUID.randomUUID();
        this.state = state;
        this.plan = plan;
        this.phase = phase;
        this.startDate = new DateTime(DateTimeZone.UTC);
        this.transitions = ImmutableList.<EffectiveSubscriptionInternalEvent>of();
    }

    SubscriptionBase sub = Mockito.mock(SubscriptionBase.class);

    @Override
    public boolean cancel(final CallContext context) throws SubscriptionBaseApiException {
        return sub.cancel(context);
    }

    @Override
    public boolean cancelWithDate(final DateTime requestedDate, final CallContext context) throws SubscriptionBaseApiException {
        return sub.cancelWithDate(requestedDate, context);
    }

    @Override
    public boolean cancelWithPolicy(BillingActionPolicy policy, CallContext context)
            throws SubscriptionBaseApiException {
        return sub.cancelWithPolicy(policy, context);
    }

    @Override
    public boolean uncancel(final CallContext context) throws SubscriptionBaseApiException {
        return sub.uncancel(context);
    }

    @Override
    public DateTime changePlan(final String productName, final BillingPeriod term, final String priceList, final List<PlanPhasePriceOverride> overrides, final CallContext context) throws SubscriptionBaseApiException {
        return sub.changePlan(productName, term, priceList, overrides, context);
    }

    @Override
    public DateTime changePlanWithDate(final String productName, final BillingPeriod term, final String priceList, final List<PlanPhasePriceOverride> overrides, final DateTime requestedDate,
                                       final CallContext context) throws SubscriptionBaseApiException {
        return sub.changePlanWithDate(productName, term, priceList, overrides, requestedDate, context);
    }

    @Override
    public DateTime changePlanWithPolicy(final String productName, final BillingPeriod term, final String priceList,
                                         final List<PlanPhasePriceOverride> overrides, final BillingActionPolicy policy, final CallContext context) throws SubscriptionBaseApiException {
        return sub.changePlanWithPolicy(productName, term, priceList, overrides, policy, context);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public DateTime getCreatedDate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public DateTime getUpdatedDate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public UUID getBundleId() {
        return bundleId;
    }

    @Override
    public EntitlementState getState() {
        return state;
    }

    @Override
    public DateTime getStartDate() {
        return startDate;
    }

    @Override
    public DateTime getEndDate() {
        return sub.getEndDate();
    }

    @Override
    public Plan getCurrentPlan() {
        return plan;
    }

    @Override
    public PriceList getCurrentPriceList() {
        return new MockPriceList();
    }

    @Override
    public PlanPhase getCurrentPhase() {
        return phase;
    }

    @Override
    public DateTime getChargedThroughDate() {
        return sub.getChargedThroughDate();
    }

    @Override
    public ProductCategory getCategory() {
        return sub.getCategory();
    }

    @Override
    public DateTime getFutureEndDate() {
        return sub.getFutureEndDate();
    }

    @Override
    public EntitlementSourceType getSourceType() {
        return sub.getSourceType();
    }

    @Override
    public Product getLastActiveProduct() {
        return sub.getLastActiveProduct();
    }

    @Override
    public PriceList getLastActivePriceList() {
        return sub.getLastActivePriceList();
    }

    @Override
    public ProductCategory getLastActiveCategory() {
        return sub.getLastActiveCategory();
    }

    @Override
    public BillingPeriod getLastActiveBillingPeriod() {
        return null;
    }

    @Override
    public Plan getLastActivePlan() {
        return sub.getLastActivePlan();
    }

    @Override
    public PlanPhase getLastActivePhase() {
        return sub.getLastActivePhase();
    }

    public void setPlan(final Plan plan) {
        this.plan = plan;
    }

    @Override
    public SubscriptionBaseTransition getPendingTransition() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public SubscriptionBaseTransition getPreviousTransition() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<SubscriptionBaseTransition> getAllTransitions() {
        return null;
    }
}
