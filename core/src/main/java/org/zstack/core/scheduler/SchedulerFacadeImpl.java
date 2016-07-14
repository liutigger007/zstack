package org.zstack.core.scheduler;

import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.Platform;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.errorcode.ErrorFacade;
import org.zstack.utils.gson.JSONObjectUtil;

import java.sql.Timestamp;
import java.util.Iterator;
import java.util.List;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

/**
 * Created by Mei Lei on 6/22/16.
 */
public class SchedulerFacadeImpl implements SchedulerFacade {
    @Autowired
    private transient CloudBus bus;
    @Autowired
    private transient ErrorFacade errf;
    @Autowired
    protected transient DatabaseFacade dbf;
    private Scheduler scheduler;

    public String getId() {
        return bus.makeLocalServiceId(SchedulerConstant.SERVICE_ID);
    }


    public boolean start() {
        List<SchedulerVO> schedulerRecords = dbf.listAll(SchedulerVO.class);
        Iterator<SchedulerVO> schedulerRecordsIterator = schedulerRecords.iterator();
        while (schedulerRecordsIterator.hasNext()) {
            SchedulerVO schedulerRecord = schedulerRecordsIterator.next();
            try {
                SchedulerJob rebootJob = (SchedulerJob) JSONObjectUtil.toObject(schedulerRecord.getJobData(), Class.forName(schedulerRecord.getJobClassName()));
                schedulerRunner(rebootJob, false);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        return true;
    }

    public boolean stop() {
        return true;
    }

    public void schedulerRunner(SchedulerJob schedulerJob) {
        schedulerRunner(schedulerJob, true);
    }

    private void schedulerRunner(SchedulerJob schedulerJob, boolean saveDB) {
        SchedulerVO vo = new SchedulerVO();
        Timestamp create = new Timestamp(System.currentTimeMillis());
        Timestamp start = new Timestamp(schedulerJob.getStartDate().getTime());
        String jobData = JSONObjectUtil.toJsonString(schedulerJob);
        String jobClassName = schedulerJob.getClass().getName();
        if (saveDB) {
            vo.setUuid(Platform.getUuid());
            vo.setSchedulerName(schedulerJob.getSchedulerName());
            vo.setStartDate(start);
            vo.setSchedulerInterval(schedulerJob.getSchedulerInterval());
            vo.setCreateDate(create);
            vo.setJobName(schedulerJob.getJobName());
            vo.setJobGroup(schedulerJob.getJobGroup());
            vo.setTriggerName(schedulerJob.getTriggerName());
            vo.setTriggerGroup(schedulerJob.getTriggerGroup());
            vo.setJobClassName(jobClassName);
            vo.setJobData(jobData);
        }

        try {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
            scheduler.start();

            JobDetail job = newJob(SchedulerRunner.class)
                    .withIdentity(schedulerJob.getJobName(), schedulerJob.getJobGroup())
                    .usingJobData("jobClassName", jobClassName)
                    .usingJobData("jobData", jobData)
                    .build();

            Trigger trigger = newTrigger()
                    .withIdentity(schedulerJob.getTriggerName(), schedulerJob.getTriggerGroup())
                    .startAt(schedulerJob.getStartDate())
                    .withSchedule(simpleSchedule()
                            .withIntervalInSeconds(schedulerJob.getSchedulerInterval())
                            .repeatForever())
                    .build();

            scheduler.scheduleJob(job, trigger);
        } catch (SchedulerException se) {
            se.printStackTrace();
        }

        if (saveDB) {
            vo.setStatus("enabled");
            dbf.persist(vo);
        }
    }
}