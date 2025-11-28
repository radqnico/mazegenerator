package it.nicoloscialpi.mazegenerator.loadbalancer;

/**
 * Jobs that need chunks loaded before execution can implement this to allow the load balancer
 * to respect the configured per-tick chunk load budget.
 */
public interface ChunkAwareJob extends LoadBalancerJob {

    /**
     * Attempt to ensure all required chunks are loaded.
     * @return true if chunks are loaded and the job can proceed this tick; false to defer.
     */
    boolean prepareChunks();
}
