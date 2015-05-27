/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.overdue.listener;

import java.util.UUID;

import javax.inject.Named;

import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.events.ControlTagCreationInternalEvent;
import org.killbill.billing.events.ControlTagDeletionInternalEvent;
import org.killbill.billing.events.InvoiceAdjustmentInternalEvent;
import org.killbill.billing.events.PaymentErrorInternalEvent;
import org.killbill.billing.events.PaymentInfoInternalEvent;
import org.killbill.billing.overdue.OverdueService;
import org.killbill.billing.overdue.api.OverdueApiException;
import org.killbill.billing.overdue.api.OverdueConfig;
import org.killbill.billing.overdue.caching.OverdueConfigCache;
import org.killbill.billing.overdue.config.DefaultOverdueConfig;
import org.killbill.billing.overdue.config.DefaultOverdueState;
import org.killbill.billing.overdue.glue.DefaultOverdueModule;
import org.killbill.billing.overdue.notification.OverdueAsyncBusNotificationKey;
import org.killbill.billing.overdue.notification.OverdueAsyncBusNotificationKey.OverdueAsyncBusNotificationAction;
import org.killbill.billing.overdue.notification.OverdueAsyncBusNotifier;
import org.killbill.billing.overdue.notification.OverduePoster;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.bus.api.BusEvent;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;

public class OverdueListener {

    private final InternalCallContextFactory internalCallContextFactory;
    private final OverduePoster asyncPoster;
    private final Clock clock;
    private final OverdueConfigCache overdueConfigCache;

    private static final Logger log = LoggerFactory.getLogger(OverdueListener.class);

    @Inject
    public OverdueListener(final Clock clock,
                           @Named(DefaultOverdueModule.OVERDUE_NOTIFIER_ASYNC_BUS_NAMED) final OverduePoster asyncPoster,
                           final OverdueConfigCache overdueConfigCache,
                           final InternalCallContextFactory internalCallContextFactory) {
        this.asyncPoster = asyncPoster;
        this.clock = clock;
        this.overdueConfigCache = overdueConfigCache;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Subscribe
    public void handle_OVERDUE_ENFORCEMENT_OFF_Insert(final ControlTagCreationInternalEvent event) {
        if (event.getTagDefinition().getName().equals(ControlTagType.OVERDUE_ENFORCEMENT_OFF.toString()) && event.getObjectType() == ObjectType.ACCOUNT) {
            insertBusEventIntoNotificationQueue(event.getObjectId(), event, OverdueAsyncBusNotificationAction.CLEAR, event.getSearchKey2());
        }
    }

    @Subscribe
    public void handle_OVERDUE_ENFORCEMENT_OFF_Removal(final ControlTagDeletionInternalEvent event) {
        if (event.getTagDefinition().getName().equals(ControlTagType.OVERDUE_ENFORCEMENT_OFF.toString()) && event.getObjectType() == ObjectType.ACCOUNT) {
            insertBusEventIntoNotificationQueue(event.getObjectId(), event, OverdueAsyncBusNotificationAction.REFRESH, event.getSearchKey2());
        }
    }

    @Subscribe
    public void handlePaymentInfoEvent(final PaymentInfoInternalEvent event) {
        log.debug("Received PaymentInfo event {}", event);
        insertBusEventIntoNotificationQueue(event.getAccountId(), event, OverdueAsyncBusNotificationAction.REFRESH, event.getSearchKey2());
    }

    @Subscribe
    public void handlePaymentErrorEvent(final PaymentErrorInternalEvent event) {
        log.debug("Received PaymentError event {}", event);
        insertBusEventIntoNotificationQueue(event.getAccountId(), event, OverdueAsyncBusNotificationAction.REFRESH, event.getSearchKey2());
    }

    @Subscribe
    public void handleInvoiceAdjustmentEvent(final InvoiceAdjustmentInternalEvent event) {
        log.debug("Received InvoiceAdjustment event {}", event);
        insertBusEventIntoNotificationQueue(event.getAccountId(), event, OverdueAsyncBusNotificationAction.REFRESH, event.getSearchKey2());
    }

    private void insertBusEventIntoNotificationQueue(final UUID accountId, final BusEvent event, final OverdueAsyncBusNotificationAction action, final Long tenantRecordId) {
        final InternalTenantContext tenantContext = internalCallContextFactory.createInternalTenantContext(tenantRecordId, null);
        final boolean shouldInsertNotification = shouldInsertNotification(tenantContext);

        if (shouldInsertNotification) {
            final OverdueAsyncBusNotificationKey notificationKey = new OverdueAsyncBusNotificationKey(accountId, action);
            asyncPoster.insertOverdueNotification(accountId, clock.getUTCNow(), OverdueAsyncBusNotifier.OVERDUE_ASYNC_BUS_NOTIFIER_QUEUE, notificationKey, createCallContext(event.getUserToken(), event.getSearchKey1(), event.getSearchKey2()));
        }
    }

    // Optimization: don't bother running the Overdue machinery if it's disabled
    private boolean shouldInsertNotification(final InternalTenantContext internalTenantContext) {
        OverdueConfig overdueConfig;
        try {
            overdueConfig = overdueConfigCache.getOverdueConfig(internalTenantContext);
        } catch (OverdueApiException e) {
            log.warn("Failed to extract overdue config for tenant " + internalTenantContext.getTenantRecordId());
            overdueConfig = null;
        }
        if (overdueConfig == null || overdueConfig.getOverdueStatesAccount() == null || overdueConfig.getOverdueStatesAccount().getStates() == null) {
            return false;
        }

        for (final DefaultOverdueState state : ((DefaultOverdueConfig) overdueConfig).getOverdueStatesAccount().getStates()) {
            if (state.getConditionEvaluation() != null) {
                return true;
            }
        }
        return false;
    }

    private InternalCallContext createCallContext(final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
        return internalCallContextFactory.createInternalCallContext(tenantRecordId, accountRecordId, "OverdueService", CallOrigin.INTERNAL, UserType.SYSTEM, userToken);
    }
}
