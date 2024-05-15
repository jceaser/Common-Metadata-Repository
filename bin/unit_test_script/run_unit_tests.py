#!/usr/bin/env python3

"""
Run all the unit tests in threads so as to get more of them done in a shorter
amount of time.
"""

from concurrent.futures import ThreadPoolExecutor, wait
from threading import Lock
import datetime
import multiprocessing
import os
import time

# Originally written with https://github.com/jceaser/shellwrap, the two files
# used have instead been included so as to not require any pip actions when
# running on bamboo ; trying to be as lights as posible
#from shellwrap import color, unix
import color
import unix

# for a more descriptive run locally, remove these settings
env = {"color": False, "verbose": color.VMode.ERROR}

# There is nothing wrong with using a couple of globals vars to manage a bunch
# of threads because this is way cleaner then doing it all in bash
# pylint: disable=global-statement
# pylint: disable=global-variable-not-assigned

lock = Lock() # used to control when the globals are written to
active_threads = 0 # pylint: disable=invalid-name
total_time = 0 # pylint: disable=invalid-name
total_jobs = 0 # pylint: disable=invalid-name
work_list = [] # will hold a list of modules from lein to test
base = os.getcwd()

opt_out = ["cmr-dev-system",
    "cmr-system-int-test",
    "cmr-oracle-lib",
    "cmr-mock-echo-app",]

run_alone = ["cmr-common-app-lib", "cmr-indexer-app", "cmr-search-app"]

# ##############################################################################

def update_active_threads_locking(amount):
    "Up the number of active threads, but make sure only one thread at a time can do this"
    global active_threads
    with lock:
        active_threads += amount

def update_total_time_locking(durration):
    "Update the total time spent, but make sure only one thread at a time can do this"
    global total_time
    with lock:
        total_time = total_time + durration

def get_work_list():
    "Get a dump of all the projects that lein manages"
    raw_work_list = unix.pipe(["lein", "dump"])
    list_of_projects = []
    for item in raw_work_list.split("\n"):
        item = item.strip(" ")
        if item.startswith("cmr-") and (item not in opt_out) and (item not in run_alone):
            list_of_projects.append(item[4:]) #remove cmr-
        else:
            print (f"skipping {item}")
    return list_of_projects

def worker(_context, id_number):
    "Function for one thread, this is run many times."
    global work_list, total_jobs, total_time, lock

    color.cprint(color.tcode.green, f"Task {id_number} started.", environment=env)

    update_active_threads_locking(1)

    while 0 < len(work_list):
        with lock:
            total_jobs = total_jobs + 1
            task = work_list.pop()

        if task is None or len(task)<1:
            color.cprint(color.tcode.red, f"restarting: {task}:{len(task)}",
                verbose=color.VMode.ERROR, environment=env)
            continue

        color.cprint(color.tcode.white, f"+task {id_number} working on {task}.",
            environment=env)

        st = time.time()
        # Run the external command in the task directory inside a try/except
        # block, to ensure thread never dies
        try:
            os.chdir(base+"/"+task)
            unix.pipe(["lein", "ci-utest"])
        except Exception as e: # pylint: disable=broad-exception-caught
            color.cprint(color.tcode.red, f"{id_number}: {task} - {e}", environment=env)
        et = time.time()
        update_total_time_locking(et-st)

        # show status
        stat_msg = f"- task {id_number} took {(et-st):.3f}s on {task}. {len(work_list)} tasks left."
        color.cprint(color.tcode.yellow, stat_msg, environment=env)
    update_active_threads_locking(-1)

    color.cprint(color.tcode.red, f"Done {id_number}", environment=env)

def main():
    " Main function, called in command line mode "
    global work_list, total_jobs, total_time
    print (f"{datetime.datetime.now()}")
    print ("This is the new script to run unit tests: run_unit_tests.py")
    with ThreadPoolExecutor() as executor:
        work_list = get_work_list()

        thread_count = int(max(2, multiprocessing.cpu_count()/2))
        color.cprint(color.tcode.yellow,
            "Using {thread_count} threads on {len(work_list)} tasks.",
            environment=env)

        jobs = []

        # Create all the worker threads
        for iindex in range(thread_count):
            jobs.append(executor.submit(worker, env, iindex))
        for future in jobs:
            result = future.result()
            if result is not None:
                print(result)
        wait(jobs)
    if active_threads>0:
        color.cprint(color.tcode.white,
            f"there are still {active_threads} running.",
            environment=env)
        time.sleep(10)
    else:
        color.cprint(color.tcode.white,
            f"{active_threads} active threads remaining.",
            environment=env)

    # There are some tests which can not run along side each other because they
    # start up services.
    color.cprint('\033[0;36m', "Starting single threads", environment=env)
    work_list = [task[4:] for task in run_alone]
    worker({}, -1)

    color.cprint(color.tcode.yellow, f"Done processing {total_jobs}", environment=env)
    color.cprint(color.tcode.yellow, f"Total: {total_time:.3f}s", environment=env)
    color.cprint(color.tcode.yellow, f"Average: {total_time/total_jobs:.3f}s", environment=env)
    print (f"{datetime.datetime.now()}")

if __name__ == "__main__":
    main()