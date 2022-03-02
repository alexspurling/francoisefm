// set up basic variables for app

const serverUrl = "http://localhost:9090";

const stationsContainer = document.querySelector('#stations');
const record = document.querySelector('#record');
const soundClips = document.querySelector('.soundClips');
const canvas = document.querySelector('.visualizer');
const loginSection = document.querySelector('#loginSection');
const nameInput = document.querySelector('.nameInput');
const enterButton = document.querySelector('#enter');
const mainSection = document.querySelector('#mainSection');
const textBlock = document.querySelector('.textBlock');
const warning = document.querySelector('.warning');

let recording = false;
let microphonePermissionGranted = false;

let chunks = [];
let mediaRecorder = undefined;
let detectedMimeType = null;

let username = '';
let frequency = '';
let userToken = getUserToken();
let locale = '';
let translations = {};

// visualiser setup - create web audio api context and canvas
let audioCtx;
const canvasCtx = canvas.getContext("2d");

history.replaceState(null, "Françoise FM");

// Retrieve translations JSON object for the given
// locale over the network
function fetchTranslationsFor(newLocale) {
    return fetch(`lang/${newLocale}.json`)
        .then(response => {
            return response.json();
        });
}

function interpolate(message, interpolationValues) {
    if (!message) {
        return message;
    }
    const r = new RegExp(/{(\w+)}/g);
    return message.replaceAll(r, (match, group1) => {
        const value = interpolationValues[group1];
        if (value) {
            return value;
        } else {
            return match;
        }
    });
}

function translate(key, interpolationValues) {
    var translationStr = translations[key];
    if (translationStr) {
        return interpolate(translationStr, interpolationValues);
    } else {
        console.log("Untranslated " + locale + " key: " + key);
        return "[" + locale + ": " + key + "]";
    }
}

function translateElement(element) {
    const key = element.getAttribute("data-i18n-key");
    if (key) {
        const optAttr = element.getAttribute("data-i18n-opt");
        let interpolationValues = {};
        if (optAttr) {
            interpolationValues = JSON.parse(optAttr) || {};
        }
        const translation = translate(key, interpolationValues);
        if (interpolationValues['attr']) {
            // The special 'attr' option means only apply the translation to the specified attribute
            element.setAttribute(interpolationValues['attr'], translation)
        } else {
            element.innerHTML = translation;
        }
    }
}

function setText(element, enValue, interpolationValues) {
    const key = Object.keys(enTranslations).find(key => enTranslations[key] === enValue);
    if (key) {
        element.setAttribute("data-i18n-key", key);
        if (interpolationValues) {
            element.setAttribute("data-i18n-opt", JSON.stringify(interpolationValues));
        }
        translateElement(element);
    } else {
        console.log("Untranslated " + locale + " string: " + enValue);
        element.innerHTML = enValue;
    }
}

function updateText(element, interpolationValues) {
    element.setAttribute("data-i18n-opt", JSON.stringify(interpolationValues));
    translateElement(element);
}

function translatePage() {
    document.querySelectorAll("[data-i18n-key]").forEach(translateElement);
}

function setLocale(newLocale) {
    if (newLocale === locale) {
        return false;
    }
    fetchTranslationsFor(newLocale)
        .then(newTranslations => {
            locale = newLocale;
            translations = newTranslations;
            translatePage();
        });
    return false;
}

function getEnTranslations() {
    // We need to fetch the english translations so we can look up english strings
    fetchTranslationsFor('en')
        .then(newTranslations => {
            enTranslations = newTranslations;
            translations = newTranslations;
        });
}

function setDefaultLocale() {
    let l = navigator.language;
    if (/^fr\b/.test(l)) {
        setLocale('fr');
    } else if (/^dk\b/.test(l)) {
        setLocale('dk');
    } else {
        setLocale('en');
    }
}

getEnTranslations();
setDefaultLocale();
loadStations();

function enterStation(username, frequency, stationFiles, dontPushState) {

    // Delay asking for the microphone a little bit because it can freeze the UI
    setTimeout(() => {
        // turn on the microphone or ask for permission to turn on the microphone
        const constraints = {audio: true};
        navigator.mediaDevices.getUserMedia(constraints).then(onMicrophoneReady, onMicrophoneError);
    }, 900);

    const stationDisplay = stationsContainer.children.length > 0;

    loginSection.classList.add(stationDisplay ? "shiftUpMore" : "shiftUp");
    mainSection.classList.add(stationDisplay ? "shiftUpMore" : "shiftUp");
    mainSection.classList.remove("mainHidden");
    loginSection.classList.add("fadeOut");
    mainSection.classList.add("fadeIn");

    // Update the text for the textBlock element with the username and frequency
    updateText(textBlock, {username, frequency});

    // Record this station in local storage
    addNewStation(username, frequency);

    stationFiles.forEach(url => {
        let fullUrl = serverUrl + url;
        let fileName = getFileFromUrl(url);
        addNewClip(fullUrl, fileName, fileName);
    });

    if (!dontPushState) {
        stateObj = {username, frequency, stationFiles}
        history.pushState(stateObj, "Françoise FM - " + username);
    }
}

window.onpopstate = function(popStateEvent) {
    console.log("event", popStateEvent)
    if (popStateEvent.state == null) {
        // User clicked back

        stopRecording();

        // Reload any newly saved stations
        loadStations();

        const stationDisplay = stationsContainer.children.length > 0;
        loginSection.classList.remove(stationDisplay ? "shiftUpMore" : "shiftUp");
        mainSection.classList.remove(stationDisplay ? "shiftUpMore" : "shiftUp");
        mainSection.classList.add("mainHidden");
        loginSection.classList.remove("fadeOut");
        mainSection.classList.remove("fadeIn");

        // Remove items from clipContainer
        soundClips.textContent = "";

        history.replaceState(null, "Françoise FM");
    } else {
        // User clicked forward
        username = popStateEvent.state.username;
        frequency = popStateEvent.state.frequency;
        enterStation(username, frequency, popStateEvent.state.stationFiles, true);
    }
}

function clickEnter() {

    username = nameInput.value;
    if (username) {
        // Get this user's previously recorded files
        let headers = {'Authorization': 'Bearer ' + Base64.encode(username + userToken)};
        let options = {
            method: 'GET',
            headers: headers,
            mode: 'cors',
            credentials: 'include'
        }
        fetch(serverUrl + '/audio', options)
            .then(response => {
                if (response.status == 200) {
                    response.json()
                        .then(station => {
                            enterStation(username, station['frequency'], station['files'])
                        });
                } else {
                    displayError("Error logging in", response)
                }
            }).catch(response => {
                displayError("Error logging in", response)
            });
    }
}

function getStations() {
    const stations = JSON.parse(localStorage.getItem("francoisefmstations"));

    if (stations) {
        return stations;
    }
    return {};
}


function loadStation(stationIdx) {
    const stations = getStations();
    nameInput.value = Object.keys(stations)[stationIdx];
    clickEnter();
}


function loadStations() {
    const stations = getStations();
    if (Object.keys(stations).length > 0) {
        var stationsContainerHTML = ""
        const entries = Object.entries(stations);
        for (var i = 0; i < entries.length - 1; i++) {
            stationsContainerHTML += "<button onclick=\"loadStation(" + i + ")\">" + entries[i][0] + " " + entries[i][1] + " FM</button>&nbsp;|&nbsp;"
        }

        const lastStation = entries[entries.length - 1]
        stationsContainerHTML += "<button onclick=\"loadStation(" + i + ")\">" + lastStation[0] + " " + lastStation[1] + " FM</button>"
        stationsContainer.innerHTML = stationsContainerHTML;
    }
}

function addNewStation(station, frequency) {
    const stations = getStations()
    stations[station] = frequency;
    localStorage.setItem("francoisefmstations", JSON.stringify(stations));
}


function addNewClip(audioURL, label, fileName) {

    const clipContainer = document.createElement('article');
    const clipLabel = document.createElement('p');
    const audio = document.createElement('audio');
    const deleteButton = document.createElement('button');

    clipContainer.classList.add('clip');
    audio.setAttribute('controls', '');
    deleteButton.className = 'delete';
    setText(deleteButton, "Delete");

    clipLabel.dataset.fileName = fileName;
    setText(clipLabel, label);

    clipContainer.appendChild(audio);
    clipContainer.appendChild(clipLabel);
    clipContainer.appendChild(deleteButton);
    soundClips.appendChild(clipContainer);
    soundClips.style.display = "block"

    audio.controls = true;
    audio.src = audioURL;

    deleteButton.onclick = function(event) {
        if (clipLabel.dataset.fileName) {
            let headers = {'Authorization': 'Bearer ' + Base64.encode(username + userToken)};
            let options = {
                method: 'DELETE',
                headers: headers,
                mode: 'cors',
                credentials: 'include'
            }
            fetch(encodeURI(serverUrl + '/audio/' + userToken + '/' + clipLabel.dataset.fileName), options)
                .then(response => {
                    let evtTgt = event.target;
                    evtTgt.parentNode.parentNode.removeChild(evtTgt.parentNode);
                }).catch(response => {
                    console.log('Error deleting clip', response)
                });
        } else {
            console.log("Cannot delete from the server because there is no file name for this clip");
            let evtTgt = event.target;
            evtTgt.parentNode.parentNode.removeChild(evtTgt.parentNode);
        }
    }

    // Return the clip label so it can be changed later
    return clipLabel;
}

function getFileFromUrl(url) {
    return url.substring(url.lastIndexOf('/') + 1)
}

function onMicrophoneReady(stream) {

    microphonePermissionGranted = true;

    mediaRecorder = new MediaRecorder(stream);

    visualize(stream);

    mediaRecorder.onstop = function(e) {
        console.log("Recording stopped");

        const blob = new Blob(chunks, {type: detectedMimeType});
        chunks = [];
        const audioURL = window.URL.createObjectURL(blob);
        let clipLabel = addNewClip(audioURL, "Saving clip...");

        console.log('Sending audio');
        // The browser should automatically set the Content-Type based on the body
        // but let's set it explicitly just in case
        let headers = {'Authorization': 'Bearer ' + Base64.encode(username + userToken), 'Content-Type': detectedMimeType};
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
                let fileName = getFileFromUrl(decodeURI(response.headers.get('Location')));
                setText(clipLabel, "Saved as {fileName}", {fileName})
                clipLabel.dataset.fileName = fileName;
            }).catch(response => {
                console.log('Error sending audio', response);
                setText(clipLabel, "Error saving clip")
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
    record.className = "record denied"
    if (err.name == "NotAllowedError") {
        displayError("Could not get microphone permission 😖", err);
    } else if (err.name == "NotFoundError") {
        displayError("No microphone found 😖", err);
    } else {
        displayError("Error: {error}", {error});
    }
}

function stopRecording() {
    if (mediaRecorder && mediaRecorder.state == "recording") {
        mediaRecorder.stop();
        console.log("Recording stopped. MediaRecorder state: ", mediaRecorder.state);
    }
    record.className = "record"
    setText(record, "Record");
    recording = false;
}

record.onclick = function() {
    console.log("Microphone permission: ", microphonePermissionGranted);
    if (!microphonePermissionGranted) {
        // Trigger request for microphone permission
        const constraints = {audio: true};
        navigator.mediaDevices.getUserMedia(constraints).then(onMicrophoneReady, onMicrophoneError);
    } else if (!mediaRecorder) {
        console.log("Something went wrong initialising media recorder")
        displayError("Something went wrong 😢")
    } else if (!recording) {
        mediaRecorder.start();
        console.log("Recording started. MediaRecorder state: ", mediaRecorder.state);
        record.className = "record recording"
        setText(record, "Stop");
        recording = true;
    } else {
        stopRecording();
    }
}

if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    displayError('Microphone access is not supported in this browser 🤨')
}

function displayError(error, obj) {
    console.log(error, obj);
    warning.style.display = "block"
    setText(warning, error);
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

// with a 12bit adc we could have 4000 different possible values
// this would be 87.0, 87.005, 87.01, 87.015, 87.02 etc


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