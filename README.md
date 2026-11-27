# Audio EQ

A modern, system-wide Android audio equalizer built with **Kotlin** and **Jetpack Compose**, featuring Material Design 3 and dynamic Monet theming. It delivers rich sound customization, headphone calibration, and acoustic profiles across Legacy, ADB/Shizuku, and Root modes.

<p align="center">
  <img src="screenshots/screenshot_1.png" width="31%" alt="10-Band Graphic Equalizer" />
  <img src="screenshots/screenshot_2.png" width="31%" alt="Sound Profiles and Enhancers" />
  <img src="screenshots/screenshot_3.png" width="31%" alt="AutoEq Calibration and Tools" />
</p>

---

## Key Features

### 1. Dual Independent Power Controls
* **Master Power Button**: Located in the top header, globally turns all equalizer and audio effects on or off.
* **Equalizer Toggle**: Located on the Equalizer card, toggles only the 10-band equalizer and presets while allowing Tone controls, Enhancers, and Subwoofer to continue working independently.

### 2. 10-Band Graphic Equalizer
* Clean, fluid vertical sliders across 10 octave bands: `31 Hz`, `62 Hz`, `125 Hz`, `250 Hz`, `500 Hz`, `1 kHz`, `2 kHz`, `4 kHz`, `8 kHz`, and `16 kHz`, backed by a 31-band `DynamicsProcessing` DSP pipeline.
* Frequency bands grouped into intuitive sections: **Bass**, **Mids**, **Treble**, and **Air**, with a live frequency response graph.
* Overall **Intensity** control (`50%` to `150%`) to scale the entire equalizer curve smoothly with a single gesture.

### 3. Built-in & Custom Equalizer Presets
* **9 Built-in Presets**: Flat, Rock, Jazz, Pop, Classical, Hip-Hop, Blues, Electronic, and Metal.
* **Custom Presets**: Adjust any slider to customize the curve, and save or delete your own named custom presets using the **Save preset** button.

### 4. Audio Profiles & Enhancers
* **Audio Profiles** (route-adapted for Built-in Speakers, Wired Headphones, USB DAC, and Bluetooth):
  * **Dynamic**: Adaptive full-range balance combining punchy bass, vocal clarity, and natural spatial width.
  * **Video**: Cinema-tuned 3D binaural surround staging with strong dialogue presence and deep low-end impact.
  * **Music**: Direct hi-fi stereo tuning with warm low-end weight and airy treble detail.
  * **Voice**: Speech-focused formant boost with low-frequency rumble reduction for podcasts, calls, and videos.
  * **Off**: Full manual control over every individual enhancer module.
* **Deep Bass**: 3-stage sub-bass extension (`38 Hz` low-shelf, `47 Hz` fundamental peak, and `210 Hz` anti-mud pocket) that stacks cleanly with Bass Enhancer or Subwoofer.
* **Bass Enhancer**: Punchy low-end boost (`0–6 dB`) with optional **Virtual Bass** harmonic synthesis for deeper bass perception on small drivers and earbuds.
* **Dialogue Enhancer**: Adjustable spoken-voice clarity boost (`0–16`) that lifts vocal formants while cleaning up low-mid mud.
* **3D Surround**: Adjustable binaural spatial width control with HRTF imaging.

### 5. Dedicated Subwoofer Module
* Independent **Subwoofer** card with adjustable **Intensity**, **Sub-Octave Mix**, **Low-Pass Crossover** (`55–120 Hz`), and **Auto Loudness** (Fletcher-Munson equal-loudness compensation at lower listening volumes).
* Automatically coordinates with Bass Enhancer to prevent double-boost distortion while stacking cleanly with Deep Bass.

### 6. AutoEq Headphone & IEM Calibration
* Integrated searchable database of headphone, earbud, and IEM measurement corrections from the AutoEq project.
* Automatic suggestion matching for connected Bluetooth and USB audio devices, with 31-band parametric or 10-band slider mapping and safe automatic headroom management.

### 7. Per-Device Profile Memory
* Automatically detects the active audio route: **Built-in Speaker**, **Wired Headphones**, **Bluetooth Audio**, or **USB DAC**.
* Independently remembers and restores presets, equalizer curves, enhancers, subwoofer settings, and tone levels for each connected output device.

### 8. Tone & Customization Tools
* **Tone Controls**: Dedicated **Bass** and **Treble** tilt controls, **Volume Contour** (ISO 226 loudness compensation), and a switchable **Knobs / Sliders** control style.
* **A/B Compare**: Instant toggle between processed audio and flat bypass.
* **Channel Balance & Split L/R EQ**: Adjust Left/Right stereo balance or tune independent equalizer curves for Left and Right channels.
* **USB DAC Bit-Perfect Mode**: Optional bit-perfect mixer routing for supported external USB DACs on compatible Android versions.
* **Selective Music-App Tracking & Theme Customization**: Choose specific apps to track in privileged modes, customize Material You seed colors and Dark/Light theme, and check for GitHub releases in-app.

### 9. Operating Modes
* **Legacy Mode (No Root / No Shizuku)**: Works universally across all device audio using Global Mix 0 (`DynamicsProcessing` 31-band/10-band or native offload EQ) plus broadcast session tracking.
* **ADB / Shizuku Mode**: Uses Shizuku to discover active app audio sessions and attach directly to app sessions (keeping Global Mix 0 disabled while app sessions are attached to prevent double-processing, with automatic Mix 0 fallback only when playback is active and no app sessions are exposed).
* **Root Mode**: Direct superuser session discovery and system-level audio control with mutually exclusive session attachment and automatic fallback.

---

## Building Releases

Release APKs are built manually via GitHub Actions (**Actions → Build and Release Android APK → Run workflow**). Pushing edits to documentation or code will not automatically trigger APK builds; manual workflow runs execute the unit test suite (`:app:testDebugUnitTest`) before assembling and publishing a signed release APK.

---

## Credits & Acknowledgements

* **[Shizuku](https://shizuku.rikka.app/)** by **[Rikka](https://github.com/RikkaApps/Shizuku)** — Enables non-root system-level session discovery and management through user-authorized elevated APIs.
* **[AutoEq](https://github.com/jaakkopasanen/AutoEq)** by **[Jaakko Pasanen](https://github.com/jaakkopasanen)** — The comprehensive open-source headphone calibration dataset and equalization profiles.

---

## License

This project is licensed under the **GNU General Public License v3.0** (GPL-3.0) — see the [LICENSE](LICENSE) file for details.
