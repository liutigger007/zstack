package org.zstack.compute.allocator;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.transaction.annotation.Transactional;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.SimpleQuery;
import org.zstack.core.db.SimpleQuery.Op;
import org.zstack.core.errorcode.ErrorFacade;
import org.zstack.header.allocator.AbstractHostAllocatorFlow;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.host.HostVO;
import org.zstack.header.storage.backup.BackupStorageVO;
import org.zstack.header.storage.backup.BackupStorageVO_;

import javax.persistence.TypedQuery;
import java.util.List;

/**
 * Created by xing5 on 2016/8/17.
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class BackupStorageSelectPrimaryStorageAllocatorFlow extends AbstractHostAllocatorFlow {
    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private PluginRegistry pluginRgty;
    @Autowired
    private ErrorFacade errf;

    @Override
    public void allocate() {
        throwExceptionIfIAmTheFirstFlow();

        if (spec.getRequiredBackupStorageUuid() == null) {
            next(candidates);
            return;
        }

        SimpleQuery<BackupStorageVO> q = dbf.createQuery(BackupStorageVO.class);
        q.select(BackupStorageVO_.type);
        q.add(BackupStorageVO_.uuid, Op.EQ, spec.getRequiredBackupStorageUuid());
        String type = q.findValue();

        List<String> possiblePrimaryStorageTypes = spec.getBackupStoragePrimaryStorageMetrics().get(type);
        if (possiblePrimaryStorageTypes == null) {
            throw new OperationFailureException(errf.stringToInternalError(
                    String.format("the image[uuid:%s] is on the backup storage[uuid:%s, type:%s] that doesn't have metrics defined" +
                            " in conf/springConfigXml/HostAllocatorManager.xml. The developer should add its primary storage metrics",
                            spec.getImage().getUuid(), spec.getRequiredBackupStorageUuid(), type)
            ));
        }

        List<HostVO> result = findHostsByPrimaryStorageUuids(possiblePrimaryStorageTypes);
        if (result.isEmpty()) {
            throw new OperationFailureException(errf.stringToOperationError(
                    String.format("The image[uuid:%s] is on the backup storage[uuid:%s, type:%s] that requires to work with primary storage[types:%s]," +
                            "however, no host found suitable to work with those primary storage", spec.getImage().getUuid(),
                            spec.getRequiredBackupStorageUuid(), type, possiblePrimaryStorageTypes)
            ));
        }

        next(result);
    }

    @Transactional(readOnly = true)
    private List<HostVO> findHostsByPrimaryStorageUuids(List<String> psTypes) {
        String sql = "select h from HostVO h, PrimaryStorageClusterRefVO ref, PrimaryStorageVO ps where ref.clusterUuid = h.clusterUuid" +
                " and ref.primaryStorageUuid = ps.uuid and ps.type in (:psTypes) and h.uuid in (:huuids)";

        TypedQuery<HostVO> q = dbf.getEntityManager().createQuery(sql, HostVO.class);
        q.setParameter("psTypes", psTypes);
        q.setParameter("huuids", getHostUuidsFromCandidates());

        return q.getResultList();
    }
}
