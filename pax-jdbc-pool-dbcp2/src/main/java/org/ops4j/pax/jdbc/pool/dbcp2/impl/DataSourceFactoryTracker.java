/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.jdbc.pool.dbcp2.impl;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

import javax.transaction.TransactionManager;

import org.ops4j.pax.jdbc.pool.dbcp2.impl.ds.PooledDataSourceFactory;
import org.ops4j.pax.jdbc.pool.dbcp2.impl.ds.XAPooledDataSourceFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.jdbc.DataSourceFactory;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Watches for DataSourceFactory services and creates/destroys a PooledDataSourceFactory for each
 * existing DataSourceFactory
 */
public class DataSourceFactoryTracker implements
    ServiceTrackerCustomizer<DataSourceFactory, Object> {

    private Logger LOG = LoggerFactory.getLogger(DataSourceFactoryTracker.class);
    private BundleContext context;

    private Map<ServiceReference<DataSourceFactory>, ServiceRegistration<DataSourceFactory>> serviceRegs;
    private TransactionManager tm;

    public DataSourceFactoryTracker(BundleContext context) {
        this(context, null);
    }

    public DataSourceFactoryTracker(BundleContext context, TransactionManager tm) {
        this.tm = tm;
        this.context = context;
        this.serviceRegs = new HashMap<ServiceReference<DataSourceFactory>, ServiceRegistration<DataSourceFactory>>();
    }

    @Override
    public Object addingService(ServiceReference<DataSourceFactory> reference) {
        if (reference.getProperty("pooled") != null) {
            // Make sure we do not react on our own service for the pooled factory
            return null;
        }
        ServiceRegistration<DataSourceFactory> reg = createAndRegisterPooledFactory(reference);
        serviceRegs.put(reference, reg);
        return context.getService(reference);
    }

    private ServiceRegistration<DataSourceFactory> createAndRegisterPooledFactory(
        ServiceReference<DataSourceFactory> reference) {
        LOG.debug("Registering PooledDataSourceFactory");
        DataSourceFactory dsf = context.getService(reference);
        PooledDataSourceFactory pdsf = (tm != null) ? new XAPooledDataSourceFactory(dsf, tm) : new PooledDataSourceFactory(dsf);
        Dictionary<String, Object> props = pdsf.createPropsForPoolingDataSourceFactory(reference);
        ServiceRegistration<DataSourceFactory> reg = context.registerService(
            DataSourceFactory.class, pdsf, props);
        return reg;
    }

    @Override
    public void modifiedService(ServiceReference<DataSourceFactory> reference, Object service) {

    }

    @Override
    public void removedService(ServiceReference<DataSourceFactory> reference, Object service) {
        ServiceRegistration<DataSourceFactory> reg = serviceRegs.get(reference);
        if (reg != null) {
            LOG.info("DataSourceFactory gone down unregistering PooledDataSourceFactory");
            reg.unregister();
        }
    }
}
