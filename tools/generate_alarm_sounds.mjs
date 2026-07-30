import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const sampleRate = 22_050;
const durationSeconds = 24;
const sampleCount = sampleRate * durationSeconds;
const outputDirectory = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../android/app/src/main/res/raw",
);

const tracks = [
  {
    file: "dawn_breeze.wav",
    pad: [[48, 52, 55, 59], [50, 54, 57, 62], [45, 52, 57, 60], [43, 50, 55, 59]],
    melody: [60, 64, 67, 71, 69, 67, 64, 62, 60, 64, 67, 72],
    bell: 0.72,
    warmth: 0.34,
  },
  {
    file: "sunrise_chimes.wav",
    pad: [[53, 57, 60, 64], [55, 59, 62, 67], [52, 55, 60, 64], [53, 57, 60, 65]],
    melody: [72, 76, 79, 76, 74, 79, 81, 79, 76, 74, 72, 76],
    bell: 0.86,
    warmth: 0.20,
  },
  {
    file: "quiet_harbor.wav",
    pad: [[45, 52, 57, 60], [41, 48, 53, 57], [43, 50, 55, 59], [40, 47, 52, 55]],
    melody: [60, 64, 67, 64, 59, 62, 67, 62, 57, 60, 64, 60],
    bell: 0.52,
    warmth: 0.48,
  },
  {
    file: "forest_light.wav",
    pad: [[55, 59, 62, 67], [57, 60, 64, 69], [52, 55, 59, 64], [54, 57, 62, 66]],
    melody: [67, 71, 74, 79, 76, 74, 71, 69, 67, 74, 71, 79],
    bell: 0.78,
    warmth: 0.26,
  },
];

function frequency(midi) {
  return 440 * 2 ** ((midi - 69) / 12);
}

function smoothStep(value) {
  const x = Math.max(0, Math.min(1, value));
  return x * x * (3 - 2 * x);
}

function padVoice(time, note, start, length, warmth) {
  const local = time - start;
  if (local < 0 || local > length) return 0;
  const attack = smoothStep(local / 1.15);
  const release = smoothStep((length - local) / 1.35);
  const envelope = attack * release;
  const hz = frequency(note);
  return envelope * (
    Math.sin(2 * Math.PI * hz * local) * 0.62 +
    Math.sin(2 * Math.PI * hz * 2 * local + 0.3) * warmth +
    Math.sin(2 * Math.PI * hz * 0.5 * local + 0.7) * 0.12
  );
}

function bellVoice(time, note, start, amount) {
  const local = time - start;
  if (local < 0 || local > 4.2) return 0;
  const attack = smoothStep(local / 0.035);
  const envelope = attack * Math.exp(-local * 0.78);
  const hz = frequency(note);
  return amount * envelope * (
    Math.sin(2 * Math.PI * hz * local) * 0.62 +
    Math.sin(2 * Math.PI * hz * 2.01 * local + 0.15) * 0.23 +
    Math.sin(2 * Math.PI * hz * 3.98 * local + 0.45) * 0.10 +
    Math.sin(2 * Math.PI * hz * 6.05 * local + 0.8) * 0.05
  );
}

function synthesize(track) {
  const samples = new Float64Array(sampleCount);
  for (let index = 0; index < sampleCount; index += 1) {
    const time = index / sampleRate;
    const section = Math.min(3, Math.floor(time / 6));
    const sectionStart = section * 6;
    let value = 0;
    for (const note of track.pad[section]) {
      value += padVoice(time, note, sectionStart, 6.15, track.warmth) * 0.105;
    }
    for (let noteIndex = 0; noteIndex < track.melody.length; noteIndex += 1) {
      value += bellVoice(
        time,
        track.melody[noteIndex],
        0.7 + noteIndex * 1.9,
        track.bell * 0.32,
      );
    }
    const opening = smoothStep(time / 0.45);
    const closing = smoothStep((durationSeconds - time) / 0.7);
    samples[index] = value * opening * closing;
  }

  let peak = 0;
  for (const sample of samples) peak = Math.max(peak, Math.abs(sample));
  const scale = peak > 0 ? 0.72 / peak : 1;
  const pcm = Buffer.alloc(sampleCount * 2);
  for (let index = 0; index < sampleCount; index += 1) {
    const value = Math.max(-1, Math.min(1, samples[index] * scale));
    pcm.writeInt16LE(Math.round(value * 32_767), index * 2);
  }
  return pcm;
}

function writeWave(filePath, pcm) {
  const header = Buffer.alloc(44);
  header.write("RIFF", 0);
  header.writeUInt32LE(36 + pcm.length, 4);
  header.write("WAVE", 8);
  header.write("fmt ", 12);
  header.writeUInt32LE(16, 16);
  header.writeUInt16LE(1, 20);
  header.writeUInt16LE(1, 22);
  header.writeUInt32LE(sampleRate, 24);
  header.writeUInt32LE(sampleRate * 2, 28);
  header.writeUInt16LE(2, 32);
  header.writeUInt16LE(16, 34);
  header.write("data", 36);
  header.writeUInt32LE(pcm.length, 40);
  fs.writeFileSync(filePath, Buffer.concat([header, pcm]));
}

fs.mkdirSync(outputDirectory, { recursive: true });
for (const track of tracks) {
  const target = path.join(outputDirectory, track.file);
  writeWave(target, synthesize(track));
  console.log(target);
}
