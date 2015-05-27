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

package org.killbill.billing.junction;

import java.math.BigDecimal;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.SubscriptionBase;

public interface BillingEvent extends Comparable<BillingEvent> {

    /**
     * @return the account that this billing event is associated with
     */
    public Account getAccount();

    /**
     * @return the billCycleDay in the account timezone as seen for that subscription at that time
     *         <p/>
     *         Note: The billCycleDay may come from the Account, or the bundle or the subscription itself
     */
    public int getBillCycleDayLocal();

    /**
     * @return the subscription
     */
    public SubscriptionBase getSubscription();

    /**
     * @return the date for when that event became effective
     */
    public DateTime getEffectiveDate();

    /**
     * @return the plan phase
     */
    public PlanPhase getPlanPhase();

    /**
     * @return the plan
     */
    public Plan getPlan();

    /**
     * @return the billing period for the active phase
     */
    public BillingPeriod getBillingPeriod();

    /**
     * @return the billing mode for the current event
     */
    public BillingMode getBillingMode();

    /**
     * @return the description of the billing event
     */
    public String getDescription();

    /**
     * @return the fixed price for the phase
     */
    public BigDecimal getFixedPrice();

    /**
     * @return the recurring price for the phase
     */
    public BigDecimal getRecurringPrice();

    /**
     * @return the currency for the account being invoiced
     */
    public Currency getCurrency();

    /**
     * @return the transition type of the underlying subscription event that triggered this
     */
    public SubscriptionBaseTransitionType getTransitionType();

    /**
     * @return a unique long indicating the ordering on which events got inserted on disk-- used for sorting only
     */
    public Long getTotalOrdering();

    /**
     * @return the TimeZone of the account
     */
    public DateTimeZone getTimeZone();

    /**
     *
     * @return the list of {@code Usage} section
     */
    public List<Usage> getUsages();
}
