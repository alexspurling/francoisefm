// set up basic variables for app

const record = document.querySelector('#record');
const soundClips = document.querySelector('.soundClips');
const canvas = document.querySelector('.visualizer');
const loginSection = document.querySelector('#loginSection');
const nameInput = document.querySelector('.nameInput');
const enterButton = document.querySelector('#enter');
const mainSection = document.querySelector('#mainSection');
const textBlock = document.querySelector('.textBlock');
const warning = document.querySelector('.warning');

var recording = false;
var microphonePermissionGranted = false;

let username = '';
let userToken = getUserToken();

// visualiser setup - create web audio api context and canvas

let audioCtx;
const canvasCtx = canvas.getContext("2d");

//main block for doing the audio recording

if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
    console.log('getUserMedia supported.');

    const constraints = { audio: true };

    let chunks = [];

    var mediaRecorder = undefined;

    var detectedMimeType = null;

    let onSuccess = function(stream) {
        mediaRecorder = new MediaRecorder(stream);

        visualize(stream);

        mediaRecorder.onstop = function(e) {
            console.log("data available after MediaRecorder.stop() called.");

            const clipContainer = document.createElement('article');
            const clipLabel = document.createElement('p');
            const audio = document.createElement('audio');
            const deleteButton = document.createElement('button');

            clipContainer.classList.add('clip');
            audio.setAttribute('controls', '');
            deleteButton.textContent = 'Delete';
            deleteButton.className = 'delete';

            clipLabel.textContent = "Saving " + username + " clip...";

            clipContainer.appendChild(audio);
            clipContainer.appendChild(clipLabel);
            clipContainer.appendChild(deleteButton);
            soundClips.appendChild(clipContainer);
            soundClips.style.display = "block"

            audio.controls = true;
            const blob = new Blob(chunks, {type: detectedMimeType});
            chunks = [];
            const audioURL = window.URL.createObjectURL(blob);
            audio.src = audioURL;
            console.log("recorder stopped");

            deleteButton.onclick = function(e) {
                let evtTgt = e.target;
                evtTgt.parentNode.parentNode.removeChild(evtTgt.parentNode);
            }

            console.log('Ok... sending audio');
            // The browser should automatically set the Content-Type based on the body
            // but let's set it explicitly just in case
            var headers = {'Authorization': 'Bearer ' + username + userToken, 'Content-Type': detectedMimeType};
            var options = {
                method: 'POST',
                headers: headers,
                body: blob,
                mode: 'cors',
                credentials: 'include'
            }
            let promise = fetch('http://localhost:7625/audio', options)
                .then(response => {
                    console.log('Finished sending audio', response);
//                    response.headers.forEach(h => { console.log(h); });
                    var location = response.headers.get('Location');
                    var fileName = location.substring(location.lastIndexOf('/') + 1)
                    clipLabel.textContent = "Saved as " + fileName;
                }).catch(response => {
                    console.log('Error sending audio', response);
                    clipLabel.textContent = "Error saving";
                });
        }

        mediaRecorder.ondataavailable = function(e) {
            chunks.push(e.data);

            if (detectedMimeType == null) {
                detectedMimeType = e.type;
            }
        }
    }

    let onError = function(err) {
        console.log('The following error occurred: ', err);
        if (err.name == "NotAllowedError") {
            record.className = "record denied"
            record.textContent = "Could not get microphone permission :(";
        } else if (err.name == "NotFoundError") {
            record.className = "record denied"
            record.textContent = "No microphone found :(";
        } else {
            record.className = "record denied"
            record.textContent = "Error: " + err;
        }
    }

    record.onclick = function() {
        console.log("Microphone permission: ", microphonePermissionGranted);
        console.log("Recording: ", recording);
        if (!microphonePermissionGranted) {
            // Trigger request for microphone permission
            navigator.mediaDevices.getUserMedia(constraints).then(onSuccess, onError);
        } else if (!mediaRecorder) {
            console.log("Something went wrong initialising media recorder")
            record.className = "record error"
            record.textContent = "Something went wrong :(";
        } else if (!recording) {
            mediaRecorder.start();
            console.log(mediaRecorder.state);
            console.log("recorder started");
            record.className = "record recording"
            record.textContent = "Stop";
            recording = true;
        } else {
            mediaRecorder.stop();
            console.log(mediaRecorder.state);
            console.log("recorder stopped");
            record.className = "record ready"
            record.textContent = "Record";
            recording = false;
        }
    }

    const onGranted = function(event) {
        console.log("Permission granted?", event);
        console.log("Permission granted:", event.target.state);
        if (event.target.state == "granted") {
            record.className = "record ready"
            record.textContent = "Record";
            microphonePermissionGranted = true;
        }
    }

    var curPermissions = navigator.permissions.query({name:'microphone'}).then(function(result) {
        console.log("Microphone permission state", result);
        if (result.state == 'granted') {
            navigator.mediaDevices.getUserMedia(constraints).then(onSuccess, onError);
            onGranted({target:result});
        } else {
            microphonePermissionGranted = false;
        }
        result.onchange = onGranted;
    }).catch(e => {
        console.log("Error querying microphone permission state", e);
        console.log("Assuming permission has been granted")
        navigator.mediaDevices.getUserMedia(constraints).then(onSuccess, onError);
        onGranted({target:{state:'granted'}});
    });

} else {
    console.log('getUserMedia not supported on this browser!');
    warning.style.display = "block"
}

function uuid() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        var rnd = Math.random() * 16 | 0, v = c === 'x' ? rnd : (rnd & 0x3 | 0x8) ;
        return v.toString(16);
    });
}

function findSubArray(arr, subarr, from_index) {
    from_index = from_index || 0;

    var i, found, j = 0;
    var last_check_index = arr.length - subarr.length;
    var subarr_length = subarr.length;

    position_loop:
    for (i = from_index; i <= last_check_index; ++i) {
        for (j = 0; j < subarr_length; ++j) {
            if (arr[i + j] !== subarr[j]) {
                continue position_loop;
            }
        }
        return i;
    }
    return -1;
};

function getUserToken() {
    var localStorageKey = 'francoisefmusertoken';
    var userToken = window.localStorage.getItem(localStorageKey)
    if (!userToken) {
        userToken = uuid();
    }
    window.localStorage.setItem(localStorageKey, userToken);
    return userToken;
}

function visualize(stream) {
    if (!audioCtx) {
        audioCtx = new AudioContext();
    }

    const source = audioCtx.createMediaStreamSource(stream);

    const analyser = audioCtx.createAnalyser();
    analyser.fftSize = 2048;
    const bufferLength = analyser.frequencyBinCount;
    const dataArray = new Uint8Array(bufferLength);

    source.connect(analyser);
    //analyser.connect(audioCtx.destination);

    draw()

    function draw() {
        const WIDTH = canvas.width
        const HEIGHT = canvas.height;

        requestAnimationFrame(draw);

        analyser.getByteTimeDomainData(dataArray);

        canvasCtx.fillStyle = 'rgb(200, 200, 200)';
        canvasCtx.fillRect(0, 0, WIDTH, HEIGHT);

        canvasCtx.lineWidth = 2;
        canvasCtx.strokeStyle = 'rgb(0, 0, 0)';

        canvasCtx.beginPath();

        let sliceWidth = WIDTH * 1.0 / bufferLength;
        let x = 0;


        for (let i = 0; i < bufferLength; i++) {

            let v = dataArray[i] / 128.0;
            let y = v * HEIGHT/2;

            if(i === 0) {
                canvasCtx.moveTo(x, y);
            } else {
                canvasCtx.lineTo(x, y);
            }

            x += sliceWidth;
        }

        canvasCtx.lineTo(canvas.width, canvas.height/2);
        canvasCtx.stroke();

    }
}

function clickEnter() {
   username = nameInput.value;
   console.log("Got username", username)
   if (username) {
       mainSection.className = "main";
       textBlock.textContent = textBlock.textContent.replace("Alex", username);
       loginSection.className = "loginHidden";
   }
}

nameInput.addEventListener('keydown', function(event) {
    console.log(event);
    if(event.key === 'Enter') {
        clickEnter();
    }
});

enterButton.addEventListener('click', clickEnter);

window.onresize = function() {
    canvas.width = mainSection.offsetWidth;
}

window.onresize();