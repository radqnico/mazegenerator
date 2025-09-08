package it.nicoloscialpi.mazegenerator.loadbalancer;

import java.util.List;

public interface JobProducer {
    List<LoadBalancerJob> getJobs();
    double getProgressPercentage();
}

