/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.entitlement;

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.entitlement.api.DefaultBlockingTransitionInternalEvent;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.EntitlementApi;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.dao.BlockingStateDao;
import org.killbill.billing.entitlement.engine.core.BlockingTransitionNotificationKey;
import org.killbill.billing.entitlement.engine.core.EntitlementNotificationKey;
import org.killbill.billing.entitlement.engine.core.EntitlementNotificationKeyAction;
import org.killbill.billing.platform.api.LifecycleHandlerType;
import org.killbill.billing.platform.api.LifecycleHandlerType.LifecycleLevel;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.bus.api.BusEvent;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueAlreadyExists;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class DefaultEntitlementService implements EntitlementService {

    public static final String NOTIFICATION_QUEUE_NAME = "entitlement-events";

    private static final Logger log = LoggerFactory.getLogger(DefaultEntitlementService.class);

    private final EntitlementApi entitlementApi;
    private final BlockingStateDao blockingStateDao;
    private final PersistentBus eventBus;
    private final NotificationQueueService notificationQueueService;
    private final InternalCallContextFactory internalCallContextFactory;

    private NotificationQueue entitlementEventQueue;

    @Inject
    public DefaultEntitlementService(final EntitlementApi entitlementApi,
                                     final BlockingStateDao blockingStateDao,
                                     final PersistentBus eventBus,
                                     final NotificationQueueService notificationQueueService,
                                     final InternalCallContextFactory internalCallContextFactory) {
        this.entitlementApi = entitlementApi;
        this.blockingStateDao = blockingStateDao;
        this.eventBus = eventBus;
        this.notificationQueueService = notificationQueueService;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public String getName() {
        return EntitlementService.ENTITLEMENT_SERVICE_NAME;
    }

    @LifecycleHandlerType(LifecycleLevel.INIT_SERVICE)
    public void initialize() {
        try {
            final NotificationQueueHandler queueHandler = new NotificationQueueHandler() {
                @Override
                public void handleReadyNotification(final NotificationEvent inputKey, final DateTime eventDateTime, final UUID fromNotificationQueueUserToken, final Long accountRecordId, final Long tenantRecordId) {
                    final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(tenantRecordId, accountRecordId, "EntitlementQueue", CallOrigin.INTERNAL, UserType.SYSTEM, fromNotificationQueueUserToken);

                    if (inputKey instanceof EntitlementNotificationKey) {
                        final CallContext callContext = internalCallContextFactory.createCallContext(internalCallContext);
                        processEntitlementNotification((EntitlementNotificationKey) inputKey, internalCallContext, callContext);
                    } else if (inputKey instanceof BlockingTransitionNotificationKey) {
                        processBlockingNotification((BlockingTransitionNotificationKey) inputKey, internalCallContext);
                    } else if (inputKey != null) {
                        log.error("Entitlement service received an unexpected event type {}" + inputKey.getClass());
                    } else {
                        log.error("Entitlement service received an unexpected null event");
                    }
                }
            };

            entitlementEventQueue = notificationQueueService.createNotificationQueue(ENTITLEMENT_SERVICE_NAME,
                                                                                     NOTIFICATION_QUEUE_NAME,
                                                                                     queueHandler);
        } catch (final NotificationQueueAlreadyExists e) {
            throw new RuntimeException(e);
        }
    }

    private void processEntitlementNotification(final EntitlementNotificationKey key, final InternalCallContext internalCallContext, final CallContext callContext) {
        final Entitlement entitlement;
        try {
            entitlement = entitlementApi.getEntitlementForId(key.getEntitlementId(), callContext);
        } catch (final EntitlementApiException e) {
            log.error("Error retrieving entitlement for id " + key.getEntitlementId(), e);
            return;
        }

        if (!(entitlement instanceof DefaultEntitlement)) {
            log.error("Entitlement service received an unexpected entitlement class type {}" + entitlement.getClass().getName());
            return;
        }

        final EntitlementNotificationKeyAction entitlementNotificationKeyAction = key.getEntitlementNotificationKeyAction();
        try {
            if (EntitlementNotificationKeyAction.CHANGE.equals(entitlementNotificationKeyAction) ||
                EntitlementNotificationKeyAction.CANCEL.equals(entitlementNotificationKeyAction)) {
                ((DefaultEntitlement) entitlement).blockAddOnsIfRequired(key.getEffectiveDate(), callContext, internalCallContext);
            } else if (EntitlementNotificationKeyAction.PAUSE.equals(entitlementNotificationKeyAction)) {
                entitlementApi.pause(key.getBundleId(), key.getEffectiveDate().toLocalDate(), callContext);
            } else if (EntitlementNotificationKeyAction.RESUME.equals(entitlementNotificationKeyAction)) {
                entitlementApi.resume(key.getBundleId(), key.getEffectiveDate().toLocalDate(), callContext);
            }
        } catch (final EntitlementApiException e) {
            log.error("Error processing event for entitlement {}" + entitlement.getId(), e);
        }
    }

    private void processBlockingNotification(final BlockingTransitionNotificationKey key, final InternalCallContext internalCallContext) {
        // Check if the blocking state has been deleted since
        if (blockingStateDao.getById(key.getBlockingStateId(), internalCallContext) == null) {
            log.debug("BlockingState {} has been deleted, not sending a bus event", key.getBlockingStateId());
            return;
        }

        final BusEvent event = new DefaultBlockingTransitionInternalEvent(key.getBlockableId(), key.getBlockingType(),
                                                                          key.isTransitionedToBlockedBilling(), key.isTransitionedToUnblockedBilling(),
                                                                          key.isTransitionedToBlockedEntitlement(), key.isTransitionToUnblockedEntitlement(),
                                                                          internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId(), internalCallContext.getUserToken());

        try {
            eventBus.post(event);
        } catch (final EventBusException e) {
            log.warn("Failed to post event {}", e);
        }
    }

    @LifecycleHandlerType(LifecycleLevel.START_SERVICE)
    public void start() {
        entitlementEventQueue.startQueue();
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() throws NoSuchNotificationQueue {
        if (entitlementEventQueue != null) {
            entitlementEventQueue.stopQueue();
            notificationQueueService.deleteNotificationQueue(entitlementEventQueue.getServiceName(), entitlementEventQueue.getQueueName());
        }
    }
}
