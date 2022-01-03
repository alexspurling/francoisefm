// set up basic variables for app

const serverUrl = "http://localhost:9090";

const record = document.querySelector('#record');
const soundClips = document.querySelector('.soundClips');
const canvas = document.querySelector('.visualizer');
const loginSection = document.querySelector('#loginSection');
const nameInput = document.querySelector('.nameInput');
const enterButton = document.querySelector('#enter');
const mainSection = document.querySelector('#mainSection');
const radio = document.querySelector('.radio');
const warning = document.querySelector('.warning');

let recording = false;
let microphonePermissionGranted = false;

let chunks = [];
let mediaRecorder = undefined;
let detectedMimeType = null;

let username = '';
let userToken = getUserToken();

// visualiser setup - create web audio api context and canvas

let audioCtx;
const canvasCtx = canvas.getContext("2d");

//main block for doing the audio recording

function addNewClip(audioURL, label) {

    const clipContainer = document.createElement('article');
    const clipLabel = document.createElement('p');
    const audio = document.createElement('audio');
    const deleteButton = document.createElement('button');

    clipContainer.classList.add('clip');
    audio.setAttribute('controls', '');
    deleteButton.textContent = 'Delete';
    deleteButton.className = 'delete';

    clipLabel.textContent = label;

    clipContainer.appendChild(audio);
    clipContainer.appendChild(clipLabel);
    clipContainer.appendChild(deleteButton);
    soundClips.appendChild(clipContainer);
    soundClips.style.display = "block"

    audio.controls = true;
    audio.src = audioURL;

    deleteButton.onclick = function(e) {
        let evtTgt = e.target;
        evtTgt.parentNode.parentNode.removeChild(evtTgt.parentNode);
    }

    // Return the clip label so it can be changed later
    return clipLabel;
}

function getFileFromUrl(url) {
    return url.substring(url.lastIndexOf('/') + 1)
}


function onMicrophoneReady(stream) {

    record.className = "record ready"
    record.textContent = "Record";
    microphonePermissionGranted = true;

    mediaRecorder = new MediaRecorder(stream);

    visualize(stream);

    mediaRecorder.onstop = function(e) {
        console.log("Recording stopped");

        const blob = new Blob(chunks, {type: detectedMimeType});
        chunks = [];
        const audioURL = window.URL.createObjectURL(blob);
        let clipLabel = addNewClip(audioURL, "Saving " + username + " clip...");

        console.log('Sending audio');
        // The browser should automatically set the Content-Type based on the body
        // but let's set it explicitly just in case
        let headers = {'Authorization': 'Bearer ' + username + userToken, 'Content-Type': detectedMimeType};
        let options = {
            method: 'POST',
            headers: headers,
            body: blob,
            mode: 'cors',
            credentials: 'include'
        }
        fetch(serverUrl + '/audio', options)
            .then(response => {
                console.log('Finished sending audio', response);
                let fileName = getFileFromUrl(response.headers.get('Location'));
                clipLabel.textContent = "Saved as " + fileName;
            }).catch(response => {
                console.log('Error sending audio', response);
                clipLabel.textContent = "Error saving";
            });
    }

    mediaRecorder.ondataavailable = function(e) {
        chunks.push(e.data);

        if (detectedMimeType == null) {
            detectedMimeType = e.data.type;
        }
    }
}

function onMicrophoneError(err) {
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
    if (!microphonePermissionGranted) {
        // Trigger request for microphone permission
        const constraints = {audio: true};
        navigator.mediaDevices.getUserMedia(constraints).then(onMicrophoneReady, onMicrophoneError);
    } else if (!mediaRecorder) {
        console.log("Something went wrong initialising media recorder")
        record.className = "record error"
        record.textContent = "Something went wrong :(";
    } else if (!recording) {
        mediaRecorder.start();
        console.log("Recording started. MediaRecorder state: ", mediaRecorder.state);
        record.className = "record recording"
        record.textContent = "Stop";
        recording = true;
    } else {
        mediaRecorder.stop();
        console.log("Recording stopped. MediaRecorder state: ", mediaRecorder.state);
        record.className = "record ready"
        record.textContent = "Record";
        recording = false;
    }
}

if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
    console.log('getUserMedia supported.');

    // If the browser supports it, check if the microphone permission has already been granted.
    // Safari doesn't support this API to protect against fingerprinting so we have to ask for permission every time.
    if (navigator.permissions) {
        let curPermissions = navigator.permissions.query({name: 'microphone'}).then(function(result) {
            console.log("Microphone permission state", result);
            if (result.state == 'granted') {
                // If permission has already been granted, then grab the microphone audio stream
                const constraints = {audio: true};
                navigator.mediaDevices.getUserMedia(constraints).then(onMicrophoneReady, onMicrophoneError);
            }
        }).catch(e => {
            // Firefox doesn't support querying for the microphone permission
            console.log("Error querying microphone permission state", e);
        });
    } else {
        console.log("Permission query not supported");
    }

} else {
    console.log('getUserMedia not supported on this browser!');
    warning.style.display = "block"
}

function uuid() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        let rnd = Math.random() * 16 | 0, v = c === 'x' ? rnd : (rnd & 0x3 | 0x8) ;
        return v.toString(16);
    });
}

function getUserToken() {
    let localStorageKey = 'francoisefmusertoken';
    let userToken = window.localStorage.getItem(localStorageKey)
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

function hash(str) {
    let hash = 0, i = 0, len = str.length;
    while (i < len) {
        hash = ((hash << 5) - hash + str.charCodeAt(i++)) << 0;
    }
    return hash;
}

// Actual radio station frequencies in the UK range from 87 to 107
// That's 107 - 87 = 20 * 10 = 200 different possible frequencies
// Let's just hash the string and then mod 200
function calculateFrequency(str) {
    return (Math.abs(hash(str)) % 200 + 870) / 10;
}

function clickEnter() {
    username = nameInput.value;
    console.log("Got username", username)
    if (username) {
        mainSection.className = "main";
        radioName = username + " " + calculateFrequency(username + userToken);
        radio.textContent = radio.textContent.replace("Alex 103.3", radioName);
        loginSection.className = "loginHidden";

        // Get this user's previously recorded files
        let headers = {'Authorization': 'Bearer ' + username + userToken};
        let options = {
            method: 'GET',
            headers: headers,
            mode: 'cors',
            credentials: 'include'
        }
        fetch(serverUrl + '/audio', options)
            .then(response => {
                // Expect to get a 404 if user has no recordings
                if (response.status == 404) {
                    return;
                }
                response.json()
                    .then(files => {
                        files.forEach(url => {
                            let fullUrl = serverUrl + url;
                            let fileName = getFileFromUrl(url);
                            addNewClip(fullUrl, fileName);
                        });
                    });
            }).catch(response => {
                console.log("Error getting audio files", response);
            });
    }
}

nameInput.addEventListener('keydown', function(event) {
    if(event.key === 'Enter') {
        clickEnter();
    }
});

enterButton.addEventListener('click', clickEnter);

window.onresize = function() {
    canvas.width = mainSection.offsetWidth;
}

window.onresize();