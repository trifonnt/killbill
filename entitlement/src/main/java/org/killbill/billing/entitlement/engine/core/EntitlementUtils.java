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

package org.killbill.billing.entitlement.engine.core;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.bus.api.BusEvent;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.clock.Clock;
import org.killbill.billing.entitlement.DefaultEntitlementService;
import org.killbill.billing.entitlement.EntitlementService;
import org.killbill.billing.entitlement.EventsStream;
import org.killbill.billing.entitlement.api.BlockingApiException;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultBlockingTransitionInternalEvent;
import org.killbill.billing.entitlement.api.DefaultEntitlementApi;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.block.BlockingChecker;
import org.killbill.billing.entitlement.block.BlockingChecker.BlockingAggregator;
import org.killbill.billing.entitlement.dao.BlockingStateDao;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public class EntitlementUtils {

    private static final Logger log = LoggerFactory.getLogger(EntitlementUtils.class);

    private final BlockingStateDao dao;
    private final BlockingChecker blockingChecker;
    private final SubscriptionBaseInternalApi subscriptionBaseInternalApi;
    private final PersistentBus eventBus;
    private final Clock clock;
    protected final NotificationQueueService notificationQueueService;

    @Inject
    public EntitlementUtils(final BlockingStateDao dao, final BlockingChecker blockingChecker,
                            final PersistentBus eventBus, final Clock clock,
                            final SubscriptionBaseInternalApi subscriptionBaseInternalApi,
                            final NotificationQueueService notificationQueueService) {
        this.dao = dao;
        this.blockingChecker = blockingChecker;
        this.eventBus = eventBus;
        this.clock = clock;
        this.subscriptionBaseInternalApi = subscriptionBaseInternalApi;
        this.notificationQueueService = notificationQueueService;
    }

    /**
     * Wrapper around BlockingStateDao#setBlockingState which will send an event on the bus if needed
     *
     * @param state   new state to store
     * @param context call context
     */
    public void setBlockingStateAndPostBlockingTransitionEvent(final BlockingState state, final InternalCallContext context) {
        final BlockingAggregator previousState = getBlockingStateFor(state.getBlockedId(), state.getType(), context);

        dao.setBlockingState(state, clock, context);

        final BlockingAggregator currentState = getBlockingStateFor(state.getBlockedId(), state.getType(), context);
        if (previousState != null && currentState != null) {
            postBlockingTransitionEvent(state.getId(), state.getEffectiveDate(), state.getBlockedId(), state.getType(), state.getService(), previousState, currentState, context);
        }
    }

    /**
     *
     * @param externalKey the bundle externalKey
     * @param tenantContext the context
     * @return the id of the first subscription (BASE or STANDALONE) that is still active for that key
     */
    public UUID getFirstActiveSubscriptionIdForKeyOrNull(final String externalKey, final InternalTenantContext tenantContext)  {

        final Iterable<UUID> nonAddonUUIDs = subscriptionBaseInternalApi.getNonAOSubscriptionIdsForKey(externalKey, tenantContext);
        return Iterables.tryFind(nonAddonUUIDs, new Predicate<UUID>() {
            @Override
            public boolean apply(final UUID input) {
                final BlockingState state = dao.getBlockingStateForService(input, BlockingStateType.SUBSCRIPTION, DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME, tenantContext);
                return (state == null || !state.getStateName().equals(DefaultEntitlementApi.ENT_STATE_CANCELLED));
            }
        }).orNull();
    }


    private BlockingAggregator getBlockingStateFor(final UUID blockableId, final BlockingStateType type, final InternalCallContext context) {
        try {
            return blockingChecker.getBlockedStatus(blockableId, type, context);
        } catch (BlockingApiException e) {
            log.warn("Failed to retrieve blocking state for {} {}", blockableId, type);
            return null;
        }
    }

    private void postBlockingTransitionEvent(final UUID blockingStateId, final DateTime effectiveDate, final UUID blockableId, final BlockingStateType type,
                                             final String serviceName, final BlockingAggregator previousState, final BlockingAggregator currentState,
                                             final InternalCallContext context) {
        final boolean isTransitionToBlockedBilling = !previousState.isBlockBilling() && currentState.isBlockBilling();
        final boolean isTransitionToUnblockedBilling = previousState.isBlockBilling() && !currentState.isBlockBilling();

        final boolean isTransitionToBlockedEntitlement = !previousState.isBlockEntitlement() && currentState.isBlockEntitlement();
        final boolean isTransitionToUnblockedEntitlement = previousState.isBlockEntitlement() && !currentState.isBlockEntitlement();

        if (effectiveDate.compareTo(clock.getUTCNow()) > 0) {
            // Add notification entry to send the bus event at the effective date
            final NotificationEvent notificationEvent = new BlockingTransitionNotificationKey(blockingStateId, blockableId, type,
                                                                                              isTransitionToBlockedBilling, isTransitionToUnblockedBilling,
                                                                                              isTransitionToBlockedEntitlement, isTransitionToUnblockedEntitlement);
            recordFutureNotification(effectiveDate, notificationEvent, context);
        } else {
            // TODO Do we want to send a DefaultEffectiveEntitlementEvent for entitlement specific blocking states?

            // Don't post if nothing has changed for entitlement-service
           if (! serviceName.equals(EntitlementService.ENTITLEMENT_SERVICE_NAME) || ! previousState.equals(currentState)) {
                final BusEvent event = new DefaultBlockingTransitionInternalEvent(blockableId, type,
                                                                                  isTransitionToBlockedBilling, isTransitionToUnblockedBilling,
                                                                                  isTransitionToBlockedEntitlement, isTransitionToUnblockedEntitlement,
                                                                                  context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken());
                postBusEvent(event);
           } else {
               System.out.println("**********   SKIPPING EVENT ");
           }

        }
    }

    private void postBusEvent(final BusEvent event) {
        try {
            // TODO STEPH Ideally we would like to post from transaction when we inserted the new blocking state, but new state would have to be recalculated from transaction which is
            // difficult without the help of BlockingChecker -- which itself relies on dao. Other alternative is duplicating the logic, or refactoring the DAO to export higher level api.
            eventBus.post(event);
        } catch (EventBusException e) {
            log.warn("Failed to post event {}", e);
        }
    }

    private void recordFutureNotification(final DateTime effectiveDate,
                                          final NotificationEvent notificationEvent,
                                          final InternalCallContext context) {
        try {
            final NotificationQueue subscriptionEventQueue = notificationQueueService.getNotificationQueue(DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                                                           DefaultEntitlementService.NOTIFICATION_QUEUE_NAME);
            subscriptionEventQueue.recordFutureNotification(effectiveDate, notificationEvent, context.getUserToken(), context.getAccountRecordId(), context.getTenantRecordId());
        } catch (NoSuchNotificationQueue e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
