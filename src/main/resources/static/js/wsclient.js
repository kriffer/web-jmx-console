let socket;

connect();

function connect() {
    socket = new WebSocket(location.protocol !== 'https:' ?
        `ws://${window.location.hostname}:8090/console` :
        `wss://${window.location.hostname}/console`);

    socket.addEventListener('open', function (event) {
        console.log("ws connection with server is open ")

    });
    socket.addEventListener('close', function (event) {
        console.log("ws connection with server is closed with code: " + event.code + " and reason: " + event.reason)
        console.log("retrying to connect... ")

        if (socket.readyState === WebSocket.CLOSED || socket.readyState === WebSocket.CLOSING) {
            connect();
            if (socket.readyState === WebSocket.OPEN) {
                console.log("Successfully reconnected.")
            }
        }

    });

    socket.addEventListener('error', function (event) {
        console.log("ws error" + event)
    });

    socket.addEventListener('message', function (event) {

        chart.data.labels.push(new Date().toLocaleTimeString().toString());

        let response = JSON.parse(event.data)

        console.log("Server response: ", response)

        if (response.status === "OK") {
            statusFlag = response.status;
            let form = document.getElementsByName('connection')[0];
            form.classList.add('d-none');

            const p = document.createElement("p");
            if (hostValue && portValue) {
                p.textContent = `You are connected to ${hostValue}:${portValue}`
            }
            if (pid) {
                p.textContent = `You are connected to ${processName}`
            }

            document.getElementById('form-container').appendChild(p)
            const button = document.createElement("button");
            button.innerHTML = "Disconnect";
            button.className = "btn btn-dark   btn-sm ";
            document.getElementById('form-container').appendChild(button)

            button.addEventListener('click', function () {
                request.status = "DISCONNECT"
                localPids.selectedIndex = 0;
                connections?connections.selectedIndex = 0:''
                request.host = ""
                request.port = ""
                request.pid = ""
                pid = ""
                console.log("Client request: ", request)
                if (socket.readyState === WebSocket.OPEN) {
                    socket.send(JSON.stringify(request));
                } else {
                    console.error("WebSocket connection is not open.");
                }
                form.classList.remove('d-none');
                button.remove()
                p.remove()

            });


        }
        if (response.status === "FAILED") {
            statusFlag = response.status;
            localPids.selectedIndex = 0;
            connections?connections.selectedIndex = 0:''
            alert(response.error);
        }


        heapCommittedLabel.textContent = `heap committed ${response.committed ? (response.committed / 1024 / 1024).toFixed(2) : ' - '}  Mb `;
        heapUsedLabel.textContent = `heap used ${response.used ? (response.used / 1024 / 1024).toFixed(2) : ' - '} Mb`;
        heapInitLabel.textContent = `heap init ${response.init ? (response.init / 1024 / 1024).toFixed(2) : ' - '} Mb`;
        heapMaxLabel.textContent = `heap max ${response.max ? (response.max / 1024 / 1024).toFixed(2) : ' - '} Mb`;

        nonHeapSizeLabel.textContent = `non-heap size ${response.nonHeapSize ? (response.nonHeapSize / 1024 / 1024).toFixed(2) : ' - '}  Mb `;
        nonHeapUsedLabel.textContent = `non-heap used ${response.nonHeapUsed ? (response.nonHeapUsed / 1024 / 1024).toFixed(2) : ' - '} Mb`;
        nonHeapInitLabel.textContent = `non-heap init ${response.nonHeapInit ? (response.nonHeapInit / 1024 / 1024).toFixed(2) : ' - '} Mb`;

        nonHeapChart.data.datasets[1].data.push((response.nonHeapSize / 1024 / 1024).toFixed(2));
        nonHeapChart.data.datasets[0].data.push((response.nonHeapUsed / 1024 / 1024).toFixed(2));
        nonHeapChart.data.datasets[2].data.push((response.nonHeapInit / 1024 / 1024).toFixed(2));

        chart.data.datasets[1].data.push((response.committed / 1024 / 1024).toFixed(2));
        chart.data.datasets[0].data.push((response.used / 1024 / 1024).toFixed(2));
        chart.data.datasets[2].data.push((response.init / 1024 / 1024).toFixed(2));
        chart.data.datasets[3].data.push((response.max / 1024 / 1024).toFixed(2));

        chartCpu.data.datasets[0].data.push((response.processCpuLoad * 1000) / 10).toFixed(2)

        chartClasses.data.datasets[0].data.push(response.loadedClassCount)
        chartThreads.data.datasets[0].data.push(response.threadCount)

        chart.update();
        chartCpu.update();
        chartClasses.update();
        chartThreads.update();
        nonHeapChart.update();

        response.name ? nameLabel.textContent = `Process name: ${response.name}` : '';
        response.pid ? pidLabel.textContent = `PID: ${response.pid}` : '';
        response.inputArguments ? inputArgumentsLabel.textContent = `Input arguments: ${response.inputArguments}` : '';
        response.vmName && response.vmVersion ? vmNameLabel.textContent = `Virtual Machine: ${response.vmName} ${response.vmVersion}` : '';
        response.vmVendor ? vmVendorLabel.textContent = `Vendor: ${response.vmVendor} ` : ''
        response.jdkVendorVersion ? jdkVendorVersion.textContent = `JDK vendor version: ${response.jdkVendorVersion} ` : ''
        response.classPath ? classPathLabel.textContent = `Class path: ${response.classPath} ` : ''
        response.libraryPath ? libraryPathLabel.textContent = `Library path: ${response.libraryPath} ` : ''
        response.startTime ? startTimeLabel.textContent = `Start time: ${startDateNormalizer(response.startTime)}` : '';
        uptimeLabel.textContent = `Uptime: ${response.uptime ? uptimeConverter(response.uptime) : ''}`;


        response.osName && response.osVersion && response.osArch ? osNameLabel.textContent = `Operating system: ${response.osName} ${response.osVersion} ${response.osArch}` : '';
        processCpuLoadLabel.textContent = `processCpuLoad  ${response.processCpuLoad ? ((response.processCpuLoad * 1000) / 10).toFixed(2) : ' - '} %`;
        totalMemorySizeLabel.textContent = `Total memory size: ${response.totalMemorySize ? (response.totalMemorySize / 1024 / 1024).toFixed(2) : ' - '} Mb`;
        freeMemorySizeLabel.textContent = `Free memory size: ${response.freeMemorySize ? (response.freeMemorySize / 1024 / 1024).toFixed(2) : ' - '}  Mb`;

        response.availableProcessors ? availableProcessorsLabel.textContent = `Available Processors: ${response.availableProcessors}` : '';

        totalSwapSpaceSizeLabel.textContent = `Total swap size: ${response.totalSwapSpaceSize ? (response.totalSwapSpaceSize / 1024 / 1024).toFixed(2) : ' - '} Mb`;
        freeSwapSpaceSizeLabel.textContent = `Free swap size: ${response.freeSwapSpaceSize ? (response.freeSwapSpaceSize / 1024 / 1024).toFixed(2) : ' - '}  Mb`;


        systemLoadAverageLabel.textContent = `System load average: ${response.systemLoadAverage ? (response.systemLoadAverage) : ' - '}`;
        loadedClassCount.textContent = `loadedClassCount ${response.loadedClassCount ? (response.loadedClassCount) : ' - '}`;
        threadCount.textContent = `threadCount ${response.threadCount ? response.threadCount : ' - '} `;


    });
}


const heapCommittedLabel = document.getElementById('heap-committed');
const heapUsedLabel = document.getElementById('heap-used');
const heapInitLabel = document.getElementById('heap-init');
const heapMaxLabel = document.getElementById('heap-max');
const nonHeapSizeLabel = document.getElementById('non-heap-size');
const nonHeapUsedLabel = document.getElementById('non-heap-used');
const nonHeapInitLabel = document.getElementById('non-heap-init');
const host = document.getElementById('host');
const port = document.getElementById('port');

let hostValue = ''
let portValue = ''

const nameLabel = document.getElementById('name_');
const pidLabel = document.getElementById('pid');
const inputArgumentsLabel = document.getElementById('inputArguments');
const vmNameLabel = document.getElementById('vmName');
const vmVendorLabel = document.getElementById('vmVendor');
const jdkVendorVersion = document.getElementById('jdkVendorVersion');
const startTimeLabel = document.getElementById('startTime');
const uptimeLabel = document.getElementById('uptime');

const osNameLabel = document.getElementById('osName');
const processCpuLoadLabel = document.getElementById('processCpuLoad');
const totalMemorySizeLabel = document.getElementById('totalMemorySize');
const freeMemorySizeLabel = document.getElementById('freeMemorySize');
const availableProcessorsLabel = document.getElementById('availableProcessors');
const totalSwapSpaceSizeLabel = document.getElementById('totalSwapSpaceSize');
const freeSwapSpaceSizeLabel = document.getElementById('freeSwapSpaceSize');
const systemLoadAverageLabel = document.getElementById('systemLoadAverage');
const classPathLabel = document.getElementById('classPath');
const libraryPathLabel = document.getElementById('libraryPath');
const loadedClassCount = document.getElementById('loadedClassCount');
const threadCount = document.getElementById('threadCount');

const heapData = []
const heapCommited = []
const heapMax = []
const heapInit = []
const nonHeapSize = []
const nonHeapInit = []
const nonHeapUsed = []
const labels = []
const cpuUsage = []
const threads = []
const classes = []
const ctx = document.getElementById('myChart');
const nonHeap = document.getElementById('nonHeapChart');
const myChartCpu = document.getElementById('myChartCpu');
const myChartThreads = document.getElementById('myChartThreads');
const myChartClasses = document.getElementById('myChartClasses');
const connectionRadio1 = document.getElementById("connectionRadio1");
const connectionRadio2 = document.getElementById("connectionRadio2");
const connections = document.getElementById("selectConnection");
const emptyConnections = document.getElementById("emptySelectConnection");
const localPids = document.getElementById("selectPid");
let statusFlag = ''
let pid = ''
let processName = ''
let request = {};


connections && connections.addEventListener("change", function () {
    const selectedValue = this.selectedIndex !== 0 ? this.options[this.selectedIndex].value : '';
    let values = []
    if (selectedValue) {
        values = selectedValue.split(':');
    } else {
        host.value = '';
        port.value = '';
        return;
    }

    host.value = values[0];
    port.value = values[1];
})

if (connectionRadio1.checked) {
    localPids.disabled = true;
}

if (connectionRadio2.checked) {
    connections ? connections.disabled = true : emptyConnections.disabled = true
}


connectionRadio1.addEventListener("change", function (e) {
    localPids.selectedIndex = 0;
    localPids.disabled = true;

    connections ? connections.disabled = false : emptyConnections.disabled = false
    host.disabled = false;
    port.disabled = false;
})

connectionRadio2.addEventListener("change", function (e) {
    host.value = '';
    port.value = '';
    connections ? connections.selectedIndex = 0 : ''
    host.disabled = true;
    port.disabled = true
    connections ? connections.disabled = true : emptyConnections.disabled = true
    localPids.disabled = false;

})

document.getElementById("selectPid").addEventListener("change", function () {
    host.value = '';
    port.value = '';

    pid = this.selectedIndex !== 0 ? this.options[this.selectedIndex].value : '';
    processName = this.selectedIndex !== 0 ? this.options[this.selectedIndex].text : '';

})
document.forms.connection.onsubmit = function () {
    // console.log(socket.readyState)
    request.host = host.value;
    request.port = port.value;
    request.pid = pid;
    request.status = "CONNECT"

    hostValue = host.value
    portValue = port.value
    console.log("Client request: ", request)
    if (socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify(request));
    } else {
        console.error("WebSocket connection is not open.");
    }
    host.value = ""
    port.value = ""


    return false;
};


let chart = new Chart(ctx, {
    type: 'line',
    data: {
        labels: labels,
        datasets: [{
            label: 'heap usage',
            data: heapData,
            borderColor: 'rgb(110,10,10)',
            backgroundColor: 'rgb(110,10,10)',
            borderWidth: 2,
            tension: 1
        },

            {
                label: 'heap commited',
                data: heapCommited,
                borderColor: 'rgb(6,108,27)',
                backgroundColor: 'rgb(6,108,27)',
                borderWidth: 2,
                tension: 1
            },

            {
                label: 'heap init',
                data: heapInit,
                borderColor: 'rgb(35,113,189)',
                backgroundColor: 'rgb(35,113,189)',
                borderWidth: 2,
                tension: 1
            },
            {
                label: 'heap max',
                data: heapMax,
                borderColor: 'rgb(147,121,8)',
                backgroundColor: 'rgb(147,121,8)',
                borderWidth: 2,
                tension: 1
            }

        ]
    }
    ,
    options: {
        responsive: true,
        plugins: {
            title: {
                display: true,
                text: 'Heap Memory Usage'
            },


        },
        elements: {
            point: {
                radius: 0
            }
        },
        scales: {
            y: {
                ticks: {
                    // forces step size to be 50 units
                    stepSize: 500
                }

            }
        }
    }
});

let nonHeapChart = new Chart(nonHeap, {
    type: 'line',
    data: {
        labels: labels,
        datasets: [{
            label: 'used',
            data: nonHeapUsed,
            borderColor: 'rgb(110,10,10)',
            backgroundColor: 'rgb(110,10,10)',
            borderWidth: 2,
            tension: 1
        },

            {
                label: 'size',
                data: nonHeapSize,
                borderColor: 'rgb(6,108,27)',
                backgroundColor: 'rgb(6,108,27)',
                borderWidth: 2,
                tension: 1
            },

            {
                label: 'init',
                data: nonHeapInit,
                borderColor: 'rgb(35,113,189)',
                backgroundColor: 'rgb(35,113,189)',
                borderWidth: 2,
                tension: 1
            },


        ]
    }
    ,
    options: {
        responsive: true,
        plugins: {
            title: {
                display: true,
                text: 'Non-Heap Memory Usage'
            },


        },
        elements: {
            point: {
                radius: 0
            }
        },
        scales: {
            y: {
                ticks: {
                    // forces step size to be 50 units
                    stepSize: 500
                }

            }
        }
    }
});

let chartCpu = new Chart(myChartCpu, {
    type: 'bar',
    data: {
        labels: labels,
        datasets: [{
            label: 'Process CPU usage (%)',
            data: cpuUsage,
            borderColor: 'rgb(214,25,25)',
            backgroundColor: 'rgb(214,25,25)',
            borderWidth: 2
        },

        ]
    }
    ,
    options: {
        responsive: true,
        plugins: {
            title: {
                display: true,
                text: 'CPU Usage'
            }
        },
        scales: {
            y: {
                min: 0,
                max: 100,
                // beginAtZero: true
                ticks: {
                    // forces step size to be 50 units
                    stepSize: 10
                }

            }
        }
    }
});


let chartThreads = new Chart(myChartThreads, {
    type: 'line',
    data: {
        labels: labels,
        datasets: [

            {
                label: 'Threads',
                data: threads,
                borderColor: 'rgb(6,108,27)',
                backgroundColor: 'rgb(6,108,27)',
                borderWidth: 2,
                tension: 1
            },

        ]
    }
    ,
    options: {
        responsive: true,
        plugins: {
            title: {
                display: true,
                text: 'Threads'
            }
        },
        elements: {
            point: {
                radius: 0
            }
        },
        scales: {
            y: {
                ticks: {
                    // forces step size to be 50 units
                    stepSize: 50
                }

            }
        }
    }
});

let chartClasses = new Chart(myChartClasses, {
    type: 'line',
    data: {
        labels: labels,
        datasets: [

            {
                label: 'Classes',
                data: classes,
                borderColor: 'rgb(35,113,189)',
                backgroundColor: 'rgb(35,113,189)',
                borderWidth: 2,
                tension: 1
            },
        ]
    }
    ,
    options: {
        responsive: true,
        plugins: {
            title: {
                display: true,
                text: 'Classes'
            }
        },
        elements: {
            point: {
                radius: 0
            }
        },
        scales: {
            y: {
                ticks: {
                    stepSize: 100
                }

            }
        }
    }
});


function startDateNormalizer(startDate) {
    let date = new Date(+startDate)
    return date.toLocaleString();
}

function uptimeConverter(uptime) {
    let seconds = +uptime / 1000
    const days = Math.floor(seconds / (24 * 3600));  // 86400 seconds in a day
    seconds %= (24 * 3600);
    const hours = Math.floor(seconds / 3600);        // 3600 seconds in an hour
    seconds %= 3600;
    const minutes = Math.floor(seconds / 60);        // 60 seconds in a minute
    const remainingSeconds = seconds % 60;

    // Build the readable format
    let result = '';
    if (days > 0) result += days + (days === 1 ? ' day, ' : ' days, ');
    if (hours > 0) result += hours + (hours === 1 ? ' hour, ' : ' hours, ');
    if (minutes > 0) result += minutes + (minutes === 1 ? ' minute, ' : ' minutes, ');
    result += remainingSeconds + (remainingSeconds === 1 ? ' second' : ' seconds');
    return result.trim();
}

window.onbeforeunload = function () {
    if (socket) {
        socket.close();
    }
};

