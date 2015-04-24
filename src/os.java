import java.util.*;

public class os {

    static int jobCount;
    static Job[] jobTable;
    static final int MAXJOBS = 50;
    static final int MAXMEMORY = 100;

    //memory management
    static int[] memory;
    static Map<Integer, Integer> freeSpace;

    //diskQueue
    static Queue<Integer> diskQueue;

    public static class Job {

        public Job() {
        }

        public Job(int jobNum, int priority, int jobSize, int maxCPUTime, int DrumArrival) {
            this.jobNum = jobNum;
            this.priority = priority;
            this.jobSize = jobSize;
            MaxCPUTime = maxCPUTime;
            this.DrumArrival = DrumArrival;
        }

        int jobNum;
        int priority;
        int jobSize;
        int MaxCPUTime;
        int DrumArrival;
        int CPUArrival;
        int startAddr;
        int timeSlice;
        int CPU_Usage;
        int CPUTimeLeft;
        boolean blocked;
        boolean inCore;
        boolean running;
    }

    public static void startup() {

        //JobTable
        jobCount = 0;
        jobTable = new Job[MAXJOBS];
        for (int i = 0; i < MAXJOBS; i++) jobTable[i] = new Job();

        //memory management
        memory = new int[MAXMEMORY];
        freeSpace = new HashMap<Integer, Integer>();
        freeSpace.put(0, MAXMEMORY);

        //I/O management
        diskQueue = new LinkedList<Integer>();

    }


    //-------------------------------------------------------SUBROUTINES-----------------------------------------------------------


    public static void saveJob(int[] p) {

        Job job = searchJobTable(p[1]);
        if (job != null && job.running) {
            //update cpu usage
            job.CPU_Usage = job.CPU_Usage + (p[5] - job.CPUArrival);

            //update cpu time left
            job.CPUTimeLeft = job.CPUTimeLeft - (p[5] - job.CPUArrival);

            //update time-slice
            if (job.timeSlice > job.CPUTimeLeft) job.timeSlice = job.CPUTimeLeft;

            //take job out of cpu
            job.running = false;
        }else {
            System.out.println("No job interrupted");
        }

    }

    /**
     * decide where to allocate job using algorithm (FIRST FIT, BEST FIT)
     * @param jobNum
     * @param jobSize
     * @return starting addr of job
     */
    public static int firstFit(int jobNum, int jobSize) {

        //traverse through freeSpace table
        for(Integer freeAddr : freeSpace.keySet()) {

            //job fits into freeSpace
            if(jobSize <= freeSpace.get(freeAddr)) {

                //allocate job into memory
                for (int j = freeAddr; j < freeAddr + jobSize; j++) memory[j] = jobNum;

                //new freespace
                freeSpace.put(freeAddr+jobSize, freeSpace.get(freeAddr) - jobSize);   //increase freeSpaceAddr, decrease freeSpaceSize

                //remove old freespace
                freeSpace.remove(freeAddr);

                return freeAddr;
            }
        }

        return -1;
    }

    public static void swapper(int jobNum, int jobSize) {

        Job job = searchJobTable(jobNum);

        //find space in memory for job
        job.startAddr = firstFit(jobNum, jobSize);
        //valid addr
        if(job.startAddr >= 0) {
            //swap job into memory
            sos.siodrum(jobNum, jobSize, job.startAddr, 0);
        } else {
            System.out.println("Job is too big for free space");
            //swap job out of memory

            job.startAddr = firstFit(jobNum, jobSize);
            sos.siodrum(jobNum, jobSize, job.startAddr, 0);
        }
    }

    public static void requestIO(int jobNum) {

        //add I/O request to diskQueue
        diskQueue.add(jobNum);

        //pop top of queue
        sos.siodisk(diskQueue.poll());
    }

    public static void blockJob(int jobNum) {
        Job job = searchJobTable(jobNum);
        job.blocked = true;
        if(jobNum == 2) job.blocked = false;
        System.out.println("Block Job " + job.jobNum);
    }

    public static void unblockJob(int jobNum) {
        Job job = searchJobTable(jobNum);
        job.blocked = false;
        System.out.println("Unblock Job " + job.jobNum);
    }

    public static void runJob(int[] a, int[] p) {

        //cpu idle
        a[0] = 1;

        //scheduler

        //loop through jobtable
        for (Job job : jobTable) {
            if(job.jobNum != 0 && job.inCore && !job.blocked) {   //found a job to run
                a[0] = 2;
                job.running = true;
                job.CPUArrival = p[5];
                p[2] = job.startAddr;
                p[3] = job.jobSize;
                p[4] = job.timeSlice;
                System.out.println("Running job " + job.jobNum);
                break;
            }
        }
    }

    public static void terminate(int jobNum, int jobSize) {

        System.out.println("Terminate job " + jobNum + " of size " + jobSize);

        int startAddr = 0, endAddr = 0; //used to update freespace

        //iterate through memory to find job
        for (int i = 0; i < MAXMEMORY; i++) {
            //found start of job
            if (jobNum == memory[i]) {
                startAddr = i; endAddr = startAddr + jobSize;
                //remove job from memory
                for (int j = startAddr; j < endAddr; j++) {
                    memory[j] = 0;
                }
                break;
            }
        }

        //printMemory();

        //iterate through jobtable to find job
        for (Job job : jobTable) {
            if (job.jobNum == jobNum) {
                job.jobNum = 0;
                job.inCore = false;
            }
        }

        //printJobTable();


        //freespace map before update
        //printFreeSpace();

        //update freespace map
        for (Integer key : freeSpace.keySet()) {
            //update freespace where job was removed
            if (key == endAddr) {
                int sizeofJob = endAddr - startAddr;
                int k = key - sizeofJob;
                int v = freeSpace.get(key) + sizeofJob;
                freeSpace.put(k, v);

                //remove old key
                freeSpace.remove(key);
            }
            break;
        }

        //freespace map after update
        //printFreeSpace();

    }

    public static Job searchJobTable(int jobNum) {
        for (Job job : jobTable) {
            if(job.jobNum == jobNum) return job;
        }
        return null;
    }

    public static void printFreeSpace() {

        System.out.println("freespace");
        for (Integer key : freeSpace.keySet()) {
            System.out.println(key + " " + freeSpace.get(key));
        }
    }

    public static void printJobTable() {
        System.out.println("Jobtable");
        for (Job job : jobTable) {
            if(job.jobNum > 0) System.out.println("Job Num " + job.jobNum + " in memory");
        }
    }

    public static void printMemory() {
        System.out.println("Memory");
        for (int i = 0; i < memory.length; i++) {
            System.out.println(i + " " + memory[i]);
        }
    }

    //---------------------------------------------------INTERRUPT HANDLERS----------------------------------------------------------

    //job enters drum
    public static void Crint (int[] a, int[] p) {

        sos.ontrace();

        //store state of interrupted job
        saveJob(p);

        //store job info in jobtable
        jobTable[jobCount].jobNum = p[1];
        jobTable[jobCount].priority = p[2];
        jobTable[jobCount].jobSize = p[3];
        jobTable[jobCount].MaxCPUTime = p[4];
        jobTable[jobCount].CPUTimeLeft = p[4];
        jobTable[jobCount].DrumArrival = p[5];
        jobTable[jobCount].timeSlice = 5;
        jobTable[jobCount].blocked = false;
        jobTable[jobCount].inCore = false;
        jobTable[jobCount].running = false;

        //long-term scheduler(Decide which job to swap into memory)

        //allocate job into memory
        swapper(p[1], p[3]);

        runJob(a, p);

        jobCount++;
    }

    //finished swapping job
    public static void Drmint (int[] a, int []p) {

        //store state of interrupted job
        saveJob(p);

        Job job = searchJobTable(p[1]);
        job.inCore = true;

        //set action and params for SOS
        runJob(a, p);

    }

    //system call
    public static void Svc (int[] a, int []p) {

        saveJob(p);

        switch(a[0]) {
            case 5: //terminate
                terminate(p[1], p[3]);
                break;
            case 6: //I/O
                requestIO(p[1]);
                break;
            case 7: //block
                blockJob(p[1]);
                break;
        }

        runJob(a, p);
    }

    //finished I/O
    public static void Dskint (int[] a, int []p) {

        saveJob(p);

        unblockJob(p[1]);

        runJob(a, p);
    }

    //job ran out of time
    public static void Tro (int[] a, int[] p) {

        saveJob(p);

        Job job = searchJobTable(p[1]);
        if (job.CPU_Usage == job.MaxCPUTime) {

            terminate(job.jobNum, job.jobSize);
        }

        runJob(a, p);
    }
}
