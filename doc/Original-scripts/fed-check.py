#!/usr/bin/env python
# This script checks the status of federated connections using qpid-route link list.

import sys
import getopt
import re
import subprocess
#import pdb

#pdb.set_trace()
#ipaddress = ''
#scac = ''

# This function reads the command line arguments and returns the iob address and the desired scac
def getArgs(argv):
    ipaddress = "10.102.6.100"
    scac = 'ALL'
    try:
        args = getopt.getopt(argv, "scac=")
        if not args: # if no option given
            return ipaddress, scac
    except getopt.GetoptError:
        print 'Usage: %s -i <ipaddress> -s <scac>' % argv[0]
        sys.exit(2)
    for arg in args:
        if arg == '-h':
            print 'Usage: %s -i <ipaddress> -s <scac>' % argv[0]
            sys.exit()
        scac = "".join(arg)
    return ipaddress, scac

# This function runs qpid-route on the specified iob and returns two stringlist one for each fed connection
# and the status of the connection
def getFedStatus (iobaddress, scac):
    out = subprocess.Popen(['qpid-route', 'link', 'list', "%s:16000" % iobaddress], stdout=subprocess.PIPE).stdout
    nameList = []
    statusList = []
    # Skip until the header dividing row is seen
    headings_done = False
    for row in out:
        if headings_done:
            fields = re.sub('\s+', ' ', row.strip()).split(' ')
            host, port, transport, durable, state, rest = fields[0], fields[1], fields[2], fields[3], fields[4], fields[5:]
            if scac == 'ALL':    
                nameList.append(host)
                if state != 'Operational':
                    state = "%s (%s)" % (state, " ".join(rest))
                statusList.append(state)
            if host.startswith(scac):
                nameList.append(host)
                if state != 'Operational':
                    state = "%s (%s)" % (state, " ".join(rest))
                statusList.append(state)
        if not headings_done and '================' in row:
            headings_done = True
    return nameList, statusList

# This function returns the status suitable for Nagios:
#   0 = all good
#   1 = warning, not all ios connections are up
#   2 = every ios connection with scac is down.
def returnStatus (nameList, statusList):
    nonWorking = 0
    working = 0
    total = len(statusList)
    # Search statusList for Operational string
    for val in statusList:
        if val == 'Operational':
            working += 1
        else:
            nonWorking+= 1
    # All connections are down
    if nonWorking == total:
        return 2
    elif working == total:
        return 0
    else:
        return 1


ipaddress, scac = getArgs(sys.argv[1:])
nameList, statusList = getFedStatus(ipaddress, scac)
status = returnStatus(nameList, statusList)
col_width = max(len(iobname) for iobname in nameList) + 12  # padding
RED = '\033[0;31m'
GREEN = '\033[0;32m'
NC = '\033[0m'
for i in range(len(nameList)):
    if statusList[i] == 'Operational':
        namecolor = GREEN + nameList[i]
        statuscolor = statusList[i] + NC
    else:
        namecolor = RED + nameList[i]
        statuscolor = statusList[i] + NC
    print '{1: <{0}}: {2}'.format(col_width, namecolor, statuscolor)
sys.exit(status)