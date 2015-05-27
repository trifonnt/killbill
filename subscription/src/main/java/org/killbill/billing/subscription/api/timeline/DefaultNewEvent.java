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
package org.killbill.billing.subscription.api.timeline;

import java.util.UUID;

import org.joda.time.DateTime;

import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseTimeline.NewEvent;

public class DefaultNewEvent implements NewEvent {

    private final UUID subscriptionId;
    private final PlanPhaseSpecifier spec;
    private final DateTime requestedDate;
    private final SubscriptionBaseTransitionType transitionType;

    public DefaultNewEvent(final UUID subscriptionId, final PlanPhaseSpecifier spec, final DateTime requestedDate, final SubscriptionBaseTransitionType transitionType) {
        this.subscriptionId = subscriptionId;
        this.spec = spec;
        this.requestedDate = requestedDate;
        this.transitionType = transitionType;
    }

    @Override
    public PlanPhaseSpecifier getPlanPhaseSpecifier() {
        return spec;
    }

    @Override
    public DateTime getRequestedDate() {
        return requestedDate;
    }

    @Override
    public SubscriptionBaseTransitionType getSubscriptionTransitionType() {
        return transitionType;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }
}
