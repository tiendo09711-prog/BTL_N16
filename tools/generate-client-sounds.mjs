import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { projectRoot } from './dev-utils.mjs';

const directory = path.join(projectRoot, 'src/main/resources/audio/client');
const sampleRate = 22050;
const cues = {
  tap: [55, [[0, 45, 620, .22], [0, 35, 940, .07]]],
  navigate: [110, [[0, 65, 600, .26], [35, 65, 800, .22]]],
  success: [240, [[0, 105, 660, .32], [80, 140, 880, .3]]],
  'bid-accepted': [200, [[0, 85, 740, .34], [65, 120, 1100, .28]]],
  outbid: [320, [[0, 115, 620, .34], [150, 145, 440, .34]]],
  'chat-sent': [100, [[0, 80, 880, .24], [25, 65, 1200, .12]]],
  'chat-received': [160, [[0, 95, 980, .22], [60, 85, 740, .22]]],
  warning: [260, [[0, 100, 560, .3], [140, 100, 560, .26]]],
  error: [230, [[0, 120, 330, .28], [85, 120, 260, .23]]],
  'auction-end': [420, [[0, 155, 660, .28], [110, 155, 550, .26], [220, 175, 440, .3]]]
};

mkdirSync(directory, { recursive: true });
for (const [name, [duration, tones]] of Object.entries(cues)) {
  const samples = Math.round(duration * sampleRate / 1000);
  const wav = Buffer.alloc(44 + samples * 2);
  wav.write('RIFF', 0); wav.writeUInt32LE(wav.length - 8, 4); wav.write('WAVEfmt ', 8);
  wav.writeUInt32LE(16, 16); wav.writeUInt16LE(1, 20); wav.writeUInt16LE(1, 22);
  wav.writeUInt32LE(sampleRate, 24); wav.writeUInt32LE(sampleRate * 2, 28);
  wav.writeUInt16LE(2, 32); wav.writeUInt16LE(16, 34); wav.write('data', 36);
  wav.writeUInt32LE(samples * 2, 40);
  for (let sample = 0; sample < samples; sample++) {
    const millis = sample * 1000 / sampleRate;
    let value = 0;
    for (const [start, length, frequency, amplitude] of tones) {
      const phase = (millis - start) / length;
      if (phase >= 0 && phase < 1) {
        const envelope = Math.sin(Math.PI * phase) ** 2;
        value += Math.sin(2 * Math.PI * frequency * (millis - start) / 1000) * envelope * amplitude;
      }
    }
    wav.writeInt16LE(Math.round(Math.max(-.9, Math.min(.9, value)) * 32767), 44 + sample * 2);
  }
  writeFileSync(path.join(directory, `${name}.wav`), wav);
}
console.log(`[AUDIO] Generated ${Object.keys(cues).length} original, deterministic PCM WAV effects.`);
