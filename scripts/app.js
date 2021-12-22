// set up basic variables for app

const record = document.querySelector('#record');
const soundClips = document.querySelector('.soundClips');
const canvas = document.querySelector('.visualizer');
const loginSection = document.querySelector('#loginSection');
const nameInput = document.querySelector('.nameInput');
const enterButton = document.querySelector('#enter');
const mainSection = document.querySelector('#mainSection');
const textBlock = document.querySelector('.textBlock');

var recording = false;
var microphonePermissionGranted = false;

// visualiser setup - create web audio api context and canvas

let audioCtx;
const canvasCtx = canvas.getContext("2d");

//main block for doing the audio recording

if (navigator.mediaDevices.getUserMedia) {
    console.log('getUserMedia supported.');

    const constraints = { audio: true };
    let chunks = [];

    var clipCount = 0;

    var mediaRecorder = undefined;
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

            clipCount += 1;
            clipLabel.textContent = username + " clip " + clipCount;

            clipContainer.appendChild(audio);
            clipContainer.appendChild(clipLabel);
            clipContainer.appendChild(deleteButton);
            soundClips.appendChild(clipContainer);
            soundClips.style.display = "block"

            audio.controls = true;
            const blob = new Blob(chunks, { 'type' : 'audio/ogg; codecs=opus' });
            chunks = [];
            const audioURL = window.URL.createObjectURL(blob);
            audio.src = audioURL;
            console.log("recorder stopped");

            deleteButton.onclick = function(e) {
                let evtTgt = e.target;
                evtTgt.parentNode.parentNode.removeChild(evtTgt.parentNode);
            }

            clipLabel.onclick = function() {
                const existingName = clipLabel.textContent;
                const newClipName = prompt('Enter a new name for your sound clip?');
                if(newClipName === null) {
                    clipLabel.textContent = existingName;
                } else {
                    clipLabel.textContent = newClipName;
                }
            }
        }

        mediaRecorder.ondataavailable = function(e) {
            chunks.push(e.data);
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
            // mediaRecorder.requestData();
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
    });

} else {
     console.log('getUserMedia not supported on your browser!');
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