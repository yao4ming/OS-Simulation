import java.util.*;

public class os {

    static int jobCount;
    static Job[] jobTable;
    static final int MAXJOBS = 50;
    static final int MAXMEMORY = 100;

    //memory management
    static int[] memory;
    static Map<Integer, Integer> freeSpace;

    //I/O Management
    static Queue<Integer> diskQueue;
    static boolean diskbusy;

    //HDD Management
    static List<Integer> drumQueue;
    static boolean drumbusy;

    public static class Job {

        int num;
        int priority;
        int size;
        int MaxCPUTime;
        int DrumArrival;
        int CPUArrival;
        int startAddr;
        int timeSlice;
        int CPU_Usage;
        int CPUTimeLeft;
        boolean inTransit;
        boolean doingIO;
        boolean blocked;
        boolean inCore;
        boolean running;
        boolean kill;
    }

    public static void startup() {

        //JobTable
        jobCount = -1;
        jobTable = new Job[MAXJOBS];
        for (int i = 0; i < MAXJOBS; i++) jobTable[i] = new Job();

        //memory management
        memory = new int[MAXMEMORY];
        freeSpace = new HashMap<Integer, Integer>();
        freeSpace.put(0, MAXMEMORY);

        //I/O management
        diskQueue = new LinkedList<Integer>();

        //drum management
        drumQueue = new ArrayList<Integer>();
        drumbusy = false;

    }

    //-------------------------------------------------------SUBROUTINES-----------------------------------------------------------

    //update cpu-usage, cpu timeleft of interrupted job
    public static void saveJob(Job job, int currentTime) {

        if (job != null && job.running) {
            //update cpu usage
            job.CPU_Usage = job.CPU_Usage + (currentTime - job.CPUArrival);

            //update cpu time left
            job.CPUTimeLeft = job.CPUTimeLeft - (currentTime - job.CPUArrival);

            //update time-slice
            if (job.timeSlice > job.CPUTimeLeft) job.timeSlice = job.CPUTimeLeft;

            //take job out of cpu
            job.running = false;
        }else {
            System.out.println("No job interrupted");
        }

    }

    //returns the start addr for job
    //return -1, job doesn't fit into freespace
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

                printMemory();

                printFreeSpace();

                return freeAddr;
            }
        }

        return -1;
    }

    //swaps job into memory
    //if memory is full, swap out a job, then swap in
    public static void swapper(int jobNum) {

        Job job = searchJobTable(jobNum);

        //find space in memory for job
        job.startAddr = firstFit(job.num, job.size);
        //valid addr
        if(job.startAddr >= 0) {
            //swap job into memory
            sos.siodrum(jobNum, job.size, job.startAddr, 0);
        } else {
            System.out.println("Job is too big for free space");
            //swap job out of memory

            job.startAddr = firstFit(job.num, job.size);
            sos.siodrum(jobNum, job.size, job.startAddr, 0);
        }
    }

    //add job into drumQueue
    //return first job in queue
    public static void requestDrum(int jobNum) {
        drumQueue.add(jobNum);
        System.out.println(drumQueue);
    }

    //add job to diskQueue
    //start I/O for first job in queue
    public static void requestIO(Job job) {

        //add I/O request to diskQueue
        diskQueue.add(job.num);
        System.out.println(diskQueue);

        //pop top of queue
        if (!diskbusy) {
            sos.siodisk(diskQueue.poll());
            job.doingIO = true;
            diskbusy = true;
        }
    }

    public static void blockJob(Job job) {
        job.blocked = true;
        System.out.println("Block Job " + job.num);
        if (!job.doingIO) {
            job.blocked = false;
            System.out.println("Unblock job b/c it is not doing I/O");
        }
    }

    public static void unblockJob(Job job) {
        job.blocked = false;
        System.out.println("Unblock Job " + job.num);
    }

    public static void swapJob() {

        if (!drumbusy) {
            //traverse drumQueue for job to swap into memory
            for (int jobNum : drumQueue) {

                Job job = searchJobTable(jobNum);

                //traverse freespace address
                for (Integer addr : freeSpace.keySet()) {

                    //job fits into freespace
                    if (job.size < freeSpace.get(addr)) {
                        swapper(jobNum);
                        drumbusy = true;
                        job.inTransit = true;
                        return;
                    }
                }
            }
        }
    }

    public static void runJob(int[] a, int[] p) {

        //cpu idle
        a[0] = 1;

        //scheduler

        //loop through jobtable
        for (Job job : jobTable) {
            if(job.num != 0 && job.inCore && !job.blocked && !job.kill) {   //found a job to run
                a[0] = 2;
                job.running = true;
                job.CPUArrival = p[5];
                p[2] = job.startAddr;
                p[3] = job.size;
                p[4] = job.timeSlice;
                System.out.println("Running job " + job.num);
                break;
            }
        }
    }

    public static Job currentJob() {
        for (Job job : jobTable) {
            if (job.running) {
                return job;
            }
        }
        return null;
    }

    public static Job jobInTransit() {
        for (Job job  : jobTable) {
            if (job.inTransit) {
                return job;
            }
        }
        return null;
    }

    public static Job jobInIO() {
        //find job that finished I/O
        for (Job job : jobTable) {
            if (job.doingIO) {
                return job;
            }
        }
        return null;
    }

    public static void updateFreeSpace(int startAddr, int endAddr, int jobSize) {
        //freespace map before update
        printFreeSpace();

        boolean after = false, before = false;
        int freeAddr = -1, freeSize = -1, newSize = -1;
        int spaceBeforeJob = -1, spaceAfterJob = -1;
        for (Integer key : freeSpace.keySet()) {

            freeAddr = key;
            freeSize = freeSpace.get(key);

            if (freeAddr + freeSize == startAddr) {     //job terminated is after freespace
                after = true;
                spaceBeforeJob = freeAddr;
            }
            else if (freeAddr == endAddr) {       //job terminated is before freespace
                before = true;
                spaceAfterJob = freeAddr;
            }
        }

        if (after && !before) {
            newSize = freeSpace.get(spaceBeforeJob) + jobSize;
            freeSpace.remove(spaceBeforeJob);
            freeSpace.put(spaceBeforeJob, newSize);
        }
        else if (before && !after) {
            newSize = freeSpace.get(spaceAfterJob) + jobSize;
            freeSpace.remove(spaceAfterJob);
            freeSpace.put(startAddr, newSize);
        }
        else if (before && after) {
            newSize = freeSpace.get(spaceBeforeJob) + jobSize + freeSpace.get(spaceAfterJob);
            freeSpace.remove(spaceBeforeJob);
            freeSpace.remove(spaceAfterJob);
            freeSpace.put(spaceBeforeJob, newSize);
        }
        else {
            freeSpace.put(startAddr, jobSize);
        }

        //freespace map after update
        printFreeSpace();

    }

    public static void terminate(Job job) {

        System.out.println("Terminate job " + job.num + " of size " + job.size);

        int startAddr = 0, endAddr = 0; //used to update freespace

        //iterate through memory to find job
        for (int i = 0; i < MAXMEMORY; i++) {
            //found start of job
            if (job.num == memory[i]) {
                startAddr = i; endAddr = startAddr + job.size;
                //remove job from memory
                for (int j = startAddr; j < endAddr; j++) {
                    memory[j] = 0;
                }
                break;
            }
        }

        printMemory();

        updateFreeSpace(startAddr, endAddr, job.size);


        job.num = 0;
        job.inCore = false;
        //printJobTable();

    }

    public static Job searchJobTable(int jobNum) {
        for (Job job : jobTable) {
            if(job.num == jobNum) return job;
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
            if(job.num > 0) System.out.println("Job Num " + job.num + " in memory");
        }
    }

    public static void printMemory() {
        System.out.println("Memory");
        for (int i = 0; i < memory.length / 5; i++) {
            int j = i + 20, k = i + 40, l = i + 60, m = i + 80;
            System.out.println(i + " " + memory[i] +
                            " \t " + j + " " + memory[j] +
                            " \t " + k + " " + memory[k] +
                            " \t " + l + " " + memory[l] +
                            " \t " + m + " " + memory[m]
            );
        }
    }

    //---------------------------------------------------INTERRUPT HANDLERS----------------------------------------------------------

    //job enters drum
    public static void Crint (int[] a, int[] p) {

        jobCount++;

        //sos.ontrace();

        Job currJob = currentJob();
        saveJob(currJob, p[5]);

        //store job info in jobtable
        jobTable[jobCount].num = p[1];
        jobTable[jobCount].priority = p[2];
        jobTable[jobCount].size = p[3];
        jobTable[jobCount].MaxCPUTime = p[4];
        jobTable[jobCount].CPUTimeLeft = p[4];
        jobTable[jobCount].DrumArrival = p[5];
        jobTable[jobCount].timeSlice = 5;
        jobTable[jobCount].blocked = false;
        jobTable[jobCount].inCore = false;
        jobTable[jobCount].running = false;

        //long-term scheduler(add job to drumQueue)
        requestDrum(p[1]);

        swapJob();
        runJob(a, p);
    }

    //finished swapping job
    public static void Drmint (int[] a, int[] p) {

        Job currJob = currentJob();
        saveJob(currJob, p[5]);

        Job job = jobInTransit();
        job.inTransit = false;
        job.inCore = true;

        //drum finished swapping job into memory
        drumbusy = false;
        drumQueue.remove(new Integer(job.num));

        swapJob();
        runJob(a, p);
    }

    //system call
    public static void Svc (int[] a, int[] p) {

        Job currJob = currentJob();
        saveJob(currJob, p[5]);

        switch(a[0]) {
            case 5: //terminate
                if (currJob.doingIO) currJob.kill = true;
                else terminate(currJob);
                break;
            case 6: //I/O
                requestIO(currJob);
                break;
            case 7: //block
                blockJob(currJob);
                break;
        }

        swapJob();
        runJob(a, p);
    }

    //finished I/O
    public static void Dskint (int[] a, int[] p) {

        Job currJob = currentJob();
        saveJob(currJob, p[5]);

        Job ioJob = jobInIO();
        ioJob.doingIO = false;
        if (ioJob.kill) terminate(ioJob);
        if (ioJob.blocked) unblockJob(ioJob);
        diskbusy = false;

        swapJob();
        runJob(a, p);
    }

    //job ran out of time
    public static void Tro (int[] a, int[] p) {

        Job currJob = currentJob();
        saveJob(currJob, p[5]);

        if (currJob.CPU_Usage == currJob.MaxCPUTime) {

            terminate(currJob);
        }

        swapJob();
        runJob(a, p);
    }
}
