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
        boolean blocked;
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

        for (Job job : jobTable) {
            if (p[1] == job.jobNum && job.running) {   //job was running and interrupted

                //update job info
            }
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

        //find space in memory for job
        jobTable[jobCount].startAddr = firstFit(jobNum, jobSize);
        if(jobTable[jobCount].startAddr >= 0) {
            //swap job into memory
            sos.siodrum(jobNum, jobSize, jobTable[jobCount].startAddr, 0);
        }else {
            System.out.println("Job is too big for free space");
            //swap job out of memory

            jobTable[jobCount].startAddr = firstFit(jobNum, jobSize);
            sos.siodrum(jobNum, jobSize, jobTable[jobCount].startAddr, 0);
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
    }

    public static void unblockJob(int jobNum) {
        Job job = searchJobTable(jobNum);
        job.blocked = false;
    }

    public static void runJob(int[] a, int[] p) {

        a[0] = 1;

        //scheduler


        for (Job job : jobTable) {  //loop through jobtable
            if(job.jobNum != 0 && !job.blocked) {   //found a job to run
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

        System.out.println("Terminate job " + jobNum + "of size " + jobSize);

        //iterate through memory to find job
        for (int i = 0; i < MAXMEMORY; i++) {
            //found start of job
            if (jobNum == memory[i]) {
                //remove job from memory
                for (int j = i; j < i + jobSize; j++) {
                    memory[j] = 0;
                }
            }
        }

        printMemory();

        //iterate through jobtable to find job
        for (int i = 0; i < MAXJOBS; i++) {
            if (jobTable[i].jobNum == jobNum) jobTable[i].jobNum = 0;   //remove job from jobtable
        }

        printJobTable();
    }

    public static Job searchJobTable(int jobNum) {
        for (Job job : jobTable) {
            if(job.jobNum == jobNum) return job;
        }
        return null;
    }

    public static void printJobTable() {
        System.out.println("Jobtable");
        for (Job job : jobTable) {
            if(job.jobNum > 0) System.out.println(job.jobNum);
        }
    }

    public static void printMemory() {
        System.out.println("Memory");
        for (int memBlock : memory) System.out.println(memBlock);
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
        jobTable[jobCount].DrumArrival = p[5];
        jobTable[jobCount].timeSlice = 4;
        jobTable[jobCount].blocked = false;
        jobTable[jobCount].running = false;

        //long-term scheduler(Decide which job to swap into memory)

        //allocate job into memory
        swapper(p[1], p[3]);

        //set action and params for SOS
        a[0] = 1;

        jobCount++;
    }

    //finished swapping job
    public static void Drmint (int[] a, int []p) {

        //store state of interrupted job
        saveJob(p);

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
    public static void Tro (int[] a, int []p) {

        saveJob(p);

        //determine whether it was time-slice or finished
        System.out.println("Job ran out of time");

        runJob(a, p);
    }


}
