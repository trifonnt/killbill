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

package org.killbill.billing.payment.glue;

import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentService;
import org.killbill.billing.payment.bus.InvoiceHandler;
import org.killbill.billing.payment.invoice.PaymentTagHandler;
import org.killbill.billing.payment.core.janitor.Janitor;
import org.killbill.billing.payment.retry.DefaultRetryService;
import org.killbill.billing.platform.api.LifecycleHandlerType;
import org.killbill.billing.platform.api.LifecycleHandlerType.LifecycleLevel;
import org.killbill.bus.api.PersistentBus;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueAlreadyExists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class DefaultPaymentService implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(DefaultPaymentService.class);

    public static final String SERVICE_NAME = "payment-service";

    private final InvoiceHandler invoiceHandler;
    private final PaymentTagHandler tagHandler;
    private final PersistentBus eventBus;
    private final PaymentApi api;
    private final DefaultRetryService retryService;
    private final Janitor janitor;

    @Inject
    public DefaultPaymentService(final InvoiceHandler invoiceHandler,
                                 final PaymentTagHandler tagHandler,
                                 final PaymentApi api,
                                 final DefaultRetryService retryService,
                                 final PersistentBus eventBus,
                                 final Janitor janitor) {
        this.invoiceHandler = invoiceHandler;
        this.tagHandler = tagHandler;
        this.eventBus = eventBus;
        this.api = api;
        this.retryService = retryService;
        this.janitor = janitor;
    }

    @Override
    public String getName() {
        return SERVICE_NAME;
    }

    @LifecycleHandlerType(LifecycleLevel.INIT_SERVICE)
    public void initialize() throws NotificationQueueAlreadyExists {
        try {
            eventBus.register(invoiceHandler);
            eventBus.register(tagHandler);
        } catch (final PersistentBus.EventBusException e) {
            log.error("Unable to register with the EventBus!", e);
        }
        retryService.initialize(SERVICE_NAME);
    }

    @LifecycleHandlerType(LifecycleLevel.START_SERVICE)
    public void start() {
        retryService.start();
        janitor.start();
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() throws NoSuchNotificationQueue {
        try {
            eventBus.unregister(invoiceHandler);
            eventBus.unregister(tagHandler);
        } catch (final PersistentBus.EventBusException e) {
            throw new RuntimeException("Unable to unregister to the EventBus!", e);
        }
        retryService.stop();
        janitor.stop();
    }

    @Override
    public PaymentApi getPaymentApi() {
        return api;
    }
}
