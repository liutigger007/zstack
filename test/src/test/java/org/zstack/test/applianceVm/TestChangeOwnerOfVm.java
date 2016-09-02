package org.zstack.test.applianceVm;

import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.componentloader.ComponentLoader;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.identity.AccountConstant;
import org.zstack.header.identity.IdentityErrors;
import org.zstack.header.identity.PolicyInventory;
import org.zstack.header.identity.SessionInventory;
import org.zstack.header.image.APICreateRootVolumeTemplateFromVolumeSnapshotMsg;
import org.zstack.header.image.ImageConstant;
import org.zstack.header.query.QueryCondition;
import org.zstack.header.storage.backup.BackupStorageInventory;
import org.zstack.header.storage.snapshot.*;
import org.zstack.header.vm.VmInstanceInventory;
import org.zstack.header.volume.APICreateDataVolumeFromVolumeSnapshotMsg;
import org.zstack.header.volume.APICreateVolumeSnapshotMsg;
import org.zstack.header.volume.VolumeConstant;
import org.zstack.header.volume.VolumeInventory;
import org.zstack.test.Api;
import org.zstack.test.ApiSenderException;
import org.zstack.test.DBUtil;
import org.zstack.test.WebBeanConstructor;
import org.zstack.test.deployer.Deployer;
import org.zstack.test.identity.IdentityCreator;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.ArrayList;

/**
 * Created by miao on 16-9-2.
 */
public class TestChangeOwnerOfVm {
    CLogger logger = Utils.getLogger(TestChangeOwnerOfVm.class);
    Deployer deployer;
    Api api;
    ComponentLoader loader;
    CloudBus bus;
    DatabaseFacade dbf;
    SessionInventory session;

    @Before
    public void setUp() throws Exception {
        DBUtil.reDeployDB();
        WebBeanConstructor con = new WebBeanConstructor();
        deployer = new Deployer("deployerXml/vm/TestChangeOwnerOfVm.xml", con);
        deployer.addSpringConfig("KVMRelated.xml");
        deployer.build();
        api = deployer.getApi();
        loader = deployer.getComponentLoader();
        bus = loader.getComponent(CloudBus.class);
        dbf = loader.getComponent(DatabaseFacade.class);
        session = api.loginAsAdmin();
    }

    @Test
    public void test() throws ApiSenderException {
        IdentityCreator identityCreator = new IdentityCreator(api);
        identityCreator.useAccount("test");
        VmInstanceInventory vm = deployer.vms.get("TestVm");
        try {
            api.changeResourceOwner(vm.getUuid(), identityCreator.getAccountSession().getAccountUuid());
        } catch (Exception e) {

        }
        identityCreator.useAccount("test2");
        api.changeResourceOwner(vm.getUuid(), identityCreator.getAccountSession().getAccountUuid());
    }
}

