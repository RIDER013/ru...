import React, { useState, useEffect, useRef } from 'react';
import {
  Home,
  Bluetooth,
  Mic,
  MicOff,
  Settings,
  ChevronUp,
  Battery,
  BatteryCharging,
  Zap,
  Check,
  AlertTriangle,
  Shield,
  Volume2,
  RefreshCw,
  Power,
  Sparkles,
  Radio,
  Sliders,
  CheckCircle2,
  X
} from 'lucide-react';

type AssistantName = 'RIDER' | 'BUDDY';

interface EarbudDevice {
  name: string;
  connected: boolean;
  leftBattery: number;
  rightBattery: number;
  caseBattery: number;
  isLeftCharging: boolean;
  isRightCharging: boolean;
  isCaseCharging: boolean;
  address: string;
}

export default function App() {
  // Navigation tabs: 'home' | 'bluetooth'
  const [activeTab, setActiveTab] = useState<'home' | 'bluetooth'>('home');

  // Assistant & Wake-word state
  const [selectedAssistant, setSelectedAssistant] = useState<AssistantName>('RIDER');
  const [accessibilityGranted, setAccessibilityGranted] = useState<boolean>(false);
  const [showAccessibilityModal, setShowAccessibilityModal] = useState<boolean>(false);
  const [pendingAssistant, setPendingAssistant] = useState<AssistantName | null>(null);

  // Listening & Voice Wake-Word
  const [isListening, setIsListening] = useState<boolean>(false);
  const [detectedWord, setDetectedWord] = useState<string | null>(null);
  const [lastSpokenText, setLastSpokenText] = useState<string>('');
  const [speechSupported, setSpeechSupported] = useState<boolean>(true);
  const [audioLevel, setAudioLevel] = useState<number>(0);

  // Gesture Swipe State
  const [isSwiping, setIsSwiping] = useState<boolean>(false);
  const [swipeCount, setSwipeCount] = useState<number>(0);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const toastTimeoutRef = useRef<number | null>(null);

  // Bluetooth Earbuds State
  const [device, setDevice] = useState<EarbudDevice>({
    name: 'Rider AirBuds Pro',
    connected: false, // Default not connected as requested
    leftBattery: 85,
    rightBattery: 90,
    caseBattery: 95,
    isLeftCharging: false,
    isRightCharging: false,
    isCaseCharging: true,
    address: '4A:26:9F:8B:12:00',
  });
  const [isScanningBluetooth, setIsScanningBluetooth] = useState<boolean>(false);

  // Speech Recognition Reference
  const recognitionRef = useRef<any>(null);
  const isListeningRef = useRef<boolean>(false);
  isListeningRef.current = isListening;

  const showToast = (msg: string, dur = 3000) => {
    if (toastTimeoutRef.current) window.clearTimeout(toastTimeoutRef.current);
    setToastMessage(msg);
    toastTimeoutRef.current = window.setTimeout(() => setToastMessage(null), dur);
  };

  // Trigger Center Upward Swipe Gesture ("screenn vati center la varchya bajula swipe karel")
  const triggerCenterSwipe = (sourceWord: string) => {
    if (!accessibilityGranted) {
      setShowAccessibilityModal(true);
      showToast('Please enable Accessibility Permission to perform swipe gestures', 3500);
      return;
    }

    setDetectedWord(sourceWord);
    setIsSwiping(true);
    setSwipeCount((prev) => prev + 1);
    showToast(`"${sourceWord}" detected! Swiping up at center...`, 2000);

    // Vibration feedback if supported
    if (typeof navigator !== 'undefined' && navigator.vibrate) {
      navigator.vibrate([40, 60, 40]);
    }

    setTimeout(() => {
      setIsSwiping(false);
      setDetectedWord(null);
    }, 850);
  };

  // Assistant selection with Accessibility permission prompt
  const handleSelectAssistant = (name: AssistantName) => {
    if (selectedAssistant === name && accessibilityGranted) {
      return;
    }

    if (!accessibilityGranted) {
      setPendingAssistant(name);
      setShowAccessibilityModal(true);
    } else {
      setSelectedAssistant(name);
      showToast(`Assistant set to "${name}". Say "${name}" to swipe up!`, 2500);
    }
  };

  const confirmAccessibilityPermission = () => {
    setAccessibilityGranted(true);
    if (pendingAssistant) {
      setSelectedAssistant(pendingAssistant);
      setPendingAssistant(null);
    }
    setShowAccessibilityModal(false);
    showToast(`Accessibility Permission Granted! "${pendingAssistant || selectedAssistant}" is active.`, 3000);
  };

  // Initialize and manage Continuous Wake-Word Speech Recognition
  const startContinuousListening = () => {
    const SpeechRecognition =
      (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;

    if (!SpeechRecognition) {
      setSpeechSupported(false);
      showToast('Speech Recognition not supported in this browser.', 3000);
      return;
    }

    try {
      if (recognitionRef.current) {
        try {
          recognitionRef.current.abort();
        } catch (_) {}
      }

      const recognition = new SpeechRecognition();
      recognitionRef.current = recognition;
      recognition.continuous = true;
      recognition.interimResults = true;
      recognition.lang = navigator.language || 'en-US';

      recognition.onstart = () => {
        setIsListening(true);
        showToast(`Wake-word active: Say "${selectedAssistant}"`, 2000);
      };

      recognition.onresult = (event: any) => {
        let transcript = '';
        for (let i = event.resultIndex; i < event.results.length; i++) {
          transcript += event.results[i][0].transcript;
        }

        const lower = transcript.toLowerCase().trim();
        setLastSpokenText(transcript);

        const currentTarget = selectedAssistant.toLowerCase();
        // Check for current selected assistant name or wake phrases
        if (
          lower.includes(currentTarget) ||
          (selectedAssistant === 'RIDER' && (lower.includes('rider') || lower.includes('ryder'))) ||
          (selectedAssistant === 'BUDDY' && (lower.includes('buddy') || lower.includes('buddie')))
        ) {
          triggerCenterSwipe(selectedAssistant);
        }
      };

      recognition.onerror = (err: any) => {
        if (err.error === 'not-allowed') {
          setIsListening(false);
          showToast('Microphone permission denied.', 3000);
        }
      };

      recognition.onend = () => {
        // Auto-restart if user still wants it active
        if (isListeningRef.current) {
          try {
            recognition.start();
          } catch (_) {
            setIsListening(false);
          }
        }
      };

      recognition.start();
      setIsListening(true);
    } catch (e) {
      setIsListening(false);
      showToast('Could not access microphone.', 2500);
    }
  };

  const stopListening = () => {
    setIsListening(false);
    if (recognitionRef.current) {
      try {
        recognitionRef.current.stop();
      } catch (_) {}
    }
    showToast('Voice wake-word paused.', 1500);
  };

  // Toggle listening
  const toggleListening = () => {
    if (isListening) {
      stopListening();
    } else {
      startContinuousListening();
    }
  };

  // Audio level visualizer simulation
  useEffect(() => {
    let interval: any;
    if (isListening) {
      interval = setInterval(() => {
        setAudioLevel(Math.floor(Math.random() * 60) + 20);
      }, 120);
    } else {
      setAudioLevel(0);
    }
    return () => clearInterval(interval);
  }, [isListening]);

  // Bluetooth scanning and pairing
  const handleBluetoothConnectToggle = async () => {
    if (device.connected) {
      setDevice((prev) => ({ ...prev, connected: false }));
      showToast('Earbuds Disconnected', 2000);
      return;
    }

    setIsScanningBluetooth(true);
    showToast('Scanning for Bluetooth Earbuds...', 1500);

    // Try Web Bluetooth if supported
    if ('bluetooth' in navigator) {
      try {
        const nav = navigator as any;
        const bleDev = await nav.bluetooth.requestDevice({
          acceptAllDevices: true,
        });
        if (bleDev) {
          setDevice({
            name: bleDev.name || 'Bluetooth Earbuds',
            connected: true,
            leftBattery: Math.floor(Math.random() * 25) + 75,
            rightBattery: Math.floor(Math.random() * 25) + 75,
            caseBattery: Math.floor(Math.random() * 15) + 85,
            isLeftCharging: false,
            isRightCharging: false,
            isCaseCharging: true,
            address: bleDev.id ? bleDev.id.slice(0, 14) : '4A:26:9F:8B:12',
          });
          setIsScanningBluetooth(false);
          showToast(`Connected to ${bleDev.name || 'Earbuds'}!`, 2500);
          return;
        }
      } catch (_) {}
    }

    // Default fast connect simulation
    setTimeout(() => {
      setIsScanningBluetooth(false);
      setDevice((prev) => ({
        ...prev,
        connected: true,
        leftBattery: 85,
        rightBattery: 88,
        caseBattery: 92,
      }));
      showToast('Connected to Rider AirBuds Pro!', 2500);
    }, 1200);
  };

  return (
    <div className="relative min-h-screen w-full bg-[#0b0f19] text-white flex flex-col justify-between font-sans overflow-x-hidden selection:bg-[#38bdf8]/30">
      
      {/* Toast Notification */}
      {toastMessage && (
        <div className="fixed top-5 left-1/2 -translate-x-1/2 z-50 px-5 py-2.5 rounded-full bg-slate-900/95 border border-sky-500/40 text-white text-xs sm:text-sm shadow-[0_10px_30px_rgba(0,0,0,0.8)] backdrop-blur-md animate-fade-in flex items-center space-x-2">
          <Sparkles className="w-4 h-4 text-[#38bdf8] animate-spin" />
          <span>{toastMessage}</span>
        </div>
      )}

      {/* CENTER UPWARD SWIPE GESTURE ANIMATION OVERLAY
          "screenn vati center la varchya bajula swipe karel" */}
      {isSwiping && (
        <div className="fixed inset-0 pointer-events-none z-40 flex flex-col items-center justify-center">
          {/* Glowing trajectory from center-bottom to center-top */}
          <div className="relative w-full h-full flex flex-col items-center justify-center">
            {/* Ambient burst */}
            <div className="absolute w-80 h-80 rounded-full bg-sky-500/15 blur-3xl animate-pulse" />

            {/* Gesture upward trail */}
            <div className="relative flex flex-col items-center">
              <div className="text-center mb-6">
                <span className="px-4 py-1.5 rounded-full bg-sky-500/20 border border-sky-400 text-sky-300 font-mono text-sm tracking-wider shadow-lg flex items-center space-x-2">
                  <ChevronUp className="w-4 h-4 animate-bounce" />
                  <span>SWIPING UP ({detectedWord})</span>
                </span>
              </div>

              {/* Animated Finger Path line */}
              <div className="relative w-2 h-72 bg-gradient-to-t from-sky-400/10 via-sky-400 to-white rounded-full shadow-[0_0_25px_#38bdf8] overflow-hidden flex flex-col justify-end">
                <div className="w-full bg-white h-24 rounded-full shadow-[0_0_20px_#ffffff] animate-ping" />
              </div>

              {/* Touch ripple circle at start */}
              <div className="w-16 h-16 rounded-full border-4 border-sky-400 bg-sky-400/20 shadow-[0_0_30px_#38bdf8] animate-ping mt-2" />
            </div>
          </div>
        </div>
      )}

      {/* TOP STATUS BAR */}
      <header className="w-full px-6 pt-5 pb-3 flex items-center justify-between border-b border-slate-800/60 bg-[#0b0f19]/80 backdrop-blur-md sticky top-0 z-30">
        <div className="flex items-center space-x-3">
          <img
            src="/assets/aistudio/image_644c82ae.png"
            alt="Rider Assistant"
            className="w-10 h-10 rounded-xl object-cover border border-sky-400/40 shadow-[0_0_12px_rgba(56,189,248,0.3)]"
          />
          <div>
            <h1 className="text-xl sm:text-2xl font-black text-transparent bg-clip-text bg-gradient-to-r from-sky-400 to-cyan-200 tracking-wider">
              RIDER ASSISTANT
            </h1>
            <p className="text-[11px] text-slate-400 tracking-wide font-medium">
              {activeTab === 'home' ? 'WAKE-WORD SWIPE DASHBOARD' : 'BLUETOOTH EARBUDS DASHBOARD'}
            </p>
          </div>
        </div>

        {/* Status badges */}
        <div className="flex items-center space-x-2.5">
          {/* Accessibility status button */}
          <button
            onClick={() => setShowAccessibilityModal(true)}
            className={`px-2.5 py-1 rounded-full text-[11px] font-semibold border flex items-center space-x-1.5 transition-all ${
              accessibilityGranted
                ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400'
                : 'bg-amber-500/10 border-amber-500/30 text-amber-400 animate-pulse'
            }`}
          >
            <Shield className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">
              {accessibilityGranted ? 'Accessibility ON' : 'Grant Permission'}
            </span>
          </button>

          {/* Bluetooth small quick badge */}
          <button
            onClick={() => setActiveTab('bluetooth')}
            className={`p-1.5 rounded-full border transition-all ${
              device.connected
                ? 'bg-sky-500/15 border-sky-500/40 text-sky-400'
                : 'bg-slate-800 border-slate-700 text-slate-400'
            }`}
            title={device.connected ? `${device.name} Connected` : 'Not Connected'}
          >
            <Bluetooth className="w-4 h-4" />
          </button>
        </div>
      </header>

      {/* MAIN FULL-SCREEN DASHBOARD CONTENT */}
      <main className="flex-1 w-full max-w-xl mx-auto flex flex-col justify-center px-4 sm:px-6 py-4">

        {/* ========================================================== */}
        {/* 1. HOME SCREEN DASHBOARD ("purn mokli")                   */}
        {/* ========================================================== */}
        {activeTab === 'home' && (
          <div className="w-full flex-1 flex flex-col justify-between py-2 sm:py-4 animate-fade-in">
            
            {/* Top Assistant Selector: "pahila rider aani dusra budy" */}
            <div className="w-full text-center">
              <span className="text-xs uppercase tracking-widest text-slate-400 font-semibold mb-2 block">
                Select Assistant Name
              </span>

              <div className="grid grid-cols-2 gap-3 sm:gap-4 p-1.5 bg-slate-900/90 border border-slate-800 rounded-2xl shadow-inner">
                {/* 1. RIDER */}
                <button
                  onClick={() => handleSelectAssistant('RIDER')}
                  className={`relative py-4 px-3 rounded-xl flex flex-col items-center justify-center space-y-1 transition-all duration-200 ${
                    selectedAssistant === 'RIDER'
                      ? 'bg-gradient-to-b from-sky-500 to-sky-700 text-white shadow-[0_0_20px_rgba(56,189,248,0.4)] border border-sky-300'
                      : 'bg-slate-800/60 text-slate-300 hover:bg-slate-800 border border-transparent'
                  }`}
                >
                  <div className="flex items-center space-x-1.5">
                    <span className="text-lg font-black tracking-wider">RIDER</span>
                    {selectedAssistant === 'RIDER' && <Check className="w-4 h-4 text-white" />}
                  </div>
                  <span className="text-[11px] opacity-80">
                    Say "Rider" to Swipe Up
                  </span>
                </button>

                {/* 2. BUDDY */}
                <button
                  onClick={() => handleSelectAssistant('BUDDY')}
                  className={`relative py-4 px-3 rounded-xl flex flex-col items-center justify-center space-y-1 transition-all duration-200 ${
                    selectedAssistant === 'BUDDY'
                      ? 'bg-gradient-to-b from-sky-500 to-sky-700 text-white shadow-[0_0_20px_rgba(56,189,248,0.4)] border border-sky-300'
                      : 'bg-slate-800/60 text-slate-300 hover:bg-slate-800 border border-transparent'
                  }`}
                >
                  <div className="flex items-center space-x-1.5">
                    <span className="text-lg font-black tracking-wider">BUDDY</span>
                    {selectedAssistant === 'BUDDY' && <Check className="w-4 h-4 text-white" />}
                  </div>
                  <span className="text-[11px] opacity-80">
                    Say "Buddy" to Swipe Up
                  </span>
                </button>
              </div>
            </div>

            {/* Center Area: Big Mic Orb & Wake Word Listener ("hllo gogle bolal ki gogle on hot tasach") */}
            <div className="my-auto py-6 sm:py-8 flex flex-col items-center justify-center text-center">
              
              {/* Pulsing wave rings when active */}
              <div className="relative flex items-center justify-center">
                {isListening && (
                  <>
                    <div
                      className="absolute rounded-full border-2 border-sky-400/30 animate-ping"
                      style={{
                        width: `${160 + audioLevel}px`,
                        height: `${160 + audioLevel}px`,
                        animationDuration: '1.8s',
                      }}
                    />
                    <div
                      className="absolute rounded-full bg-sky-500/10 blur-xl animate-pulse"
                      style={{
                        width: `${180 + audioLevel * 1.2}px`,
                        height: `${180 + audioLevel * 1.2}px`,
                      }}
                    />
                  </>
                )}

                {/* Main Mic Button */}
                <button
                  onClick={toggleListening}
                  className={`relative w-28 h-28 sm:w-32 sm:h-32 rounded-full flex flex-col items-center justify-center transition-all duration-300 shadow-2xl z-10 ${
                    isListening
                      ? 'bg-gradient-to-tr from-sky-500 via-sky-400 to-cyan-300 text-slate-950 shadow-[0_0_40px_#38bdf8] scale-105'
                      : 'bg-slate-800/90 hover:bg-slate-700 text-slate-400 hover:text-white border-2 border-slate-700'
                  }`}
                >
                  {isListening ? (
                    <>
                      <Mic className="w-10 h-10 sm:w-12 sm:h-12 animate-pulse" />
                      <span className="text-[10px] font-black uppercase tracking-wider mt-1">LISTENING</span>
                    </>
                  ) : (
                    <>
                      <MicOff className="w-10 h-10 sm:w-12 sm:h-12 opacity-60" />
                      <span className="text-[10px] font-semibold uppercase tracking-wider mt-1">TAP TO START</span>
                    </>
                  )}
                </button>
              </div>

              {/* Status & Speech detection info */}
              <div className="mt-6 max-w-sm">
                <div className="text-sm font-semibold text-white">
                  {isListening ? (
                    <span className="text-sky-400 flex items-center justify-center space-x-2">
                      <Radio className="w-4 h-4 animate-spin text-sky-400" />
                      <span>Always Listening for "{selectedAssistant}"...</span>
                    </span>
                  ) : (
                    <span className="text-slate-400">Microphone is Paused</span>
                  )}
                </div>

                <p className="text-xs text-slate-400 mt-1 leading-relaxed">
                  Just like "Hey Google", speak <strong className="text-sky-300 font-bold">"{selectedAssistant}"</strong> anytime to trigger a swipe up at the center of the screen.
                </p>

                {/* Heard audio transcript if any */}
                {lastSpokenText && (
                  <div className="mt-3 px-3 py-1.5 rounded-lg bg-slate-900/80 border border-slate-800 text-xs text-slate-300 inline-block font-mono">
                    Heard: "{lastSpokenText}"
                  </div>
                )}
              </div>
            </div>

            {/* Bottom Actions: Simulated Quick Trigger & Stats */}
            <div className="w-full space-y-3">
              {/* Quick test buttons (useful if mic isn't available or for instant testing) */}
              <div className="flex items-center justify-center space-x-2 text-xs">
                <span className="text-slate-500 text-[11px]">Instant test:</span>
                <button
                  onClick={() => triggerCenterSwipe(selectedAssistant)}
                  className="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-sky-400 border border-slate-700/80 transition-all font-medium flex items-center space-x-1.5 active:scale-95"
                >
                  <ChevronUp className="w-3.5 h-3.5" />
                  <span>Say "{selectedAssistant}"</span>
                </button>
                <button
                  onClick={() => triggerCenterSwipe('Rider swipe up')}
                  className="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700/80 transition-all font-medium active:scale-95"
                >
                  <span>"Swipe Up"</span>
                </button>
              </div>

              {/* Swipe Counter / Accessibility status */}
              <div className="p-3 bg-slate-900/60 border border-slate-800/80 rounded-xl flex items-center justify-between text-xs text-slate-400">
                <div className="flex items-center space-x-2">
                  <div className="w-2 h-2 rounded-full bg-sky-400" />
                  <span>Swipes performed: <strong className="text-white">{swipeCount}</strong></span>
                </div>
                <div className="flex items-center space-x-1.5 text-slate-400">
                  <span>Target:</span>
                  <span className="font-mono text-sky-400">Center ➔ Top</span>
                </div>
              </div>
            </div>

          </div>
        )}

        {/* ========================================================== */}
        {/* 2. BLUETOOTH SCREEN DASHBOARD ("purn mokli")              */}
        {/* ========================================================== */}
        {activeTab === 'bluetooth' && (
          <div className="w-full flex-1 flex flex-col justify-between py-2 sm:py-4 animate-fade-in">
            
            {/* When NOT CONNECTED:
                "nasel tar fkt caase cha icone aani not conected as chotas name oky" */}
            {!device.connected ? (
              <div className="my-auto flex flex-col items-center justify-center text-center py-10">
                
                {/* Center Case Icon */}
                <div className="relative mb-6">
                  <div className="w-32 h-32 sm:w-36 sm:h-36 rounded-3xl bg-slate-900 border-2 border-slate-700 flex items-center justify-center shadow-2xl relative overflow-hidden group">
                    <EarbudsCaseSvg open={false} charging={false} />
                  </div>
                  {/* Status dot */}
                  <div className="absolute -bottom-2 -right-2 p-1.5 rounded-full bg-slate-950 border border-slate-800">
                    <div className="w-3 h-3 rounded-full bg-rose-500 animate-pulse" />
                  </div>
                </div>

                {/* Small "Not Connected" text */}
                <h3 className="text-base sm:text-lg font-bold text-slate-200">
                  Not Connected
                </h3>
                <p className="text-xs text-slate-500 mt-1 max-w-xs">
                  No Bluetooth earbuds connected. Tap below to search and connect your device.
                </p>

                {/* Connect / Scan Button */}
                <button
                  onClick={handleBluetoothConnectToggle}
                  disabled={isScanningBluetooth}
                  className="mt-8 px-8 py-3.5 rounded-xl bg-gradient-to-r from-sky-500 to-sky-600 hover:from-sky-400 hover:to-sky-500 active:scale-95 text-white font-semibold text-sm shadow-[0_0_25px_rgba(56,189,248,0.3)] transition-all flex items-center space-x-2"
                >
                  <RefreshCw className={`w-4 h-4 ${isScanningBluetooth ? 'animate-spin' : ''}`} />
                  <span>{isScanningBluetooth ? 'Searching Earbuds...' : 'Connect Earbuds'}</span>
                </button>
              </div>
            ) : (
              /* When CONNECTED:
                 "asel tar tyach naav ... left la left side chya air birds cha chitra aani right la ride side cya yaach oky aani khali tyanchi battr ylavele asel oky" */
              <div className="w-full flex-1 flex flex-col justify-between py-2">
                
                {/* Device Header */}
                <div className="text-center pt-2 pb-4">
                  <div className="inline-flex items-center space-x-2 px-3 py-1 rounded-full bg-sky-500/10 border border-sky-500/30 text-sky-400 text-xs font-semibold mb-1">
                    <div className="w-2 h-2 rounded-full bg-sky-400 animate-ping" />
                    <span>CONNECTED</span>
                  </div>
                  <h2 className="text-2xl sm:text-3xl font-extrabold text-white tracking-wide">
                    {device.name}
                  </h2>
                  <p className="text-xs font-mono text-slate-500 mt-0.5">
                    MAC: {device.address}
                  </p>
                </div>

                {/* Earbuds & Case Visual Layout:
                    LEFT: Left Earbud Image
                    CENTER: Case Image
                    RIGHT: Right Earbud Image */}
                <div className="my-auto py-6 grid grid-cols-3 gap-2 sm:gap-4 items-center">
                  
                  {/* 1. LEFT EARBUD */}
                  <div className="flex flex-col items-center bg-slate-900/60 border border-slate-800 rounded-2xl p-3 sm:p-4 hover:border-sky-500/40 transition-colors shadow-lg">
                    <span className="text-[11px] font-bold text-sky-400 tracking-wider mb-2">
                      LEFT (L)
                    </span>
                    <div className="w-20 h-28 sm:w-24 sm:h-32 flex items-center justify-center">
                      <LeftEarbudSvg />
                    </div>
                    {/* Battery Level below */}
                    <div className="w-full mt-3 pt-2 border-t border-slate-800/80 text-center">
                      <div className="flex items-center justify-center space-x-1 text-sm font-bold text-white">
                        <Battery className="w-4 h-4 text-emerald-400" />
                        <span>{device.leftBattery}%</span>
                      </div>
                      <div className="w-full bg-slate-800 h-1.5 rounded-full mt-1.5 overflow-hidden">
                        <div
                          className="bg-emerald-400 h-full rounded-full transition-all duration-500"
                          style={{ width: `${device.leftBattery}%` }}
                        />
                      </div>
                    </div>
                  </div>

                  {/* 2. CHARGING CASE */}
                  <div className="flex flex-col items-center bg-slate-900/90 border border-sky-500/30 rounded-2xl p-3 sm:p-4 shadow-[0_0_20px_rgba(56,189,248,0.15)]">
                    <span className="text-[11px] font-bold text-slate-300 tracking-wider mb-2">
                      CASE
                    </span>
                    <div className="w-20 h-28 sm:w-24 sm:h-32 flex items-center justify-center">
                      <EarbudsCaseSvg open={true} charging={device.isCaseCharging} />
                    </div>
                    {/* Battery Level below */}
                    <div className="w-full mt-3 pt-2 border-t border-slate-800/80 text-center">
                      <div className="flex items-center justify-center space-x-1 text-sm font-bold text-white">
                        <BatteryCharging className="w-4 h-4 text-sky-400" />
                        <span>{device.caseBattery}%</span>
                      </div>
                      <div className="w-full bg-slate-800 h-1.5 rounded-full mt-1.5 overflow-hidden">
                        <div
                          className="bg-sky-400 h-full rounded-full transition-all duration-500"
                          style={{ width: `${device.caseBattery}%` }}
                        />
                      </div>
                    </div>
                  </div>

                  {/* 3. RIGHT EARBUD */}
                  <div className="flex flex-col items-center bg-slate-900/60 border border-slate-800 rounded-2xl p-3 sm:p-4 hover:border-sky-500/40 transition-colors shadow-lg">
                    <span className="text-[11px] font-bold text-sky-400 tracking-wider mb-2">
                      RIGHT (R)
                    </span>
                    <div className="w-20 h-28 sm:w-24 sm:h-32 flex items-center justify-center">
                      <RightEarbudSvg />
                    </div>
                    {/* Battery Level below */}
                    <div className="w-full mt-3 pt-2 border-t border-slate-800/80 text-center">
                      <div className="flex items-center justify-center space-x-1 text-sm font-bold text-white">
                        <Battery className="w-4 h-4 text-emerald-400" />
                        <span>{device.rightBattery}%</span>
                      </div>
                      <div className="w-full bg-slate-800 h-1.5 rounded-full mt-1.5 overflow-hidden">
                        <div
                          className="bg-emerald-400 h-full rounded-full transition-all duration-500"
                          style={{ width: `${device.rightBattery}%` }}
                        />
                      </div>
                    </div>
                  </div>

                </div>

                {/* Bottom Device Controls */}
                <div className="space-y-3 pt-2">
                  <div className="grid grid-cols-2 gap-3">
                    <button
                      onClick={() => {
                        // Simulate battery refresh
                        setDevice((prev) => ({
                          ...prev,
                          leftBattery: Math.max(10, prev.leftBattery - Math.floor(Math.random() * 4)),
                          rightBattery: Math.max(10, prev.rightBattery - Math.floor(Math.random() * 4)),
                        }));
                        showToast('Battery levels refreshed', 1500);
                      }}
                      className="py-3 px-4 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 text-xs font-semibold flex items-center justify-center space-x-2"
                    >
                      <RefreshCw className="w-3.5 h-3.5 text-sky-400" />
                      <span>Refresh Battery</span>
                    </button>

                    <button
                      onClick={handleBluetoothConnectToggle}
                      className="py-3 px-4 rounded-xl bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 border border-rose-500/30 text-xs font-semibold flex items-center justify-center space-x-2"
                    >
                      <Power className="w-3.5 h-3.5" />
                      <span>Disconnect</span>
                    </button>
                  </div>
                </div>

              </div>
            )}

          </div>
        )}

      </main>

      {/* BOTTOM NAVIGATION BAR: Home & Bluetooth */}
      <footer className="w-full max-w-xl mx-auto px-6 py-3 border-t border-slate-800/80 bg-[#0b0f19]/90 backdrop-blur-md sticky bottom-0 z-30">
        <div className="grid grid-cols-2 gap-3">
          {/* Home Button */}
          <button
            onClick={() => setActiveTab('home')}
            className={`py-3 px-4 rounded-2xl flex items-center justify-center space-x-2 font-semibold text-sm transition-all duration-200 ${
              activeTab === 'home'
                ? 'bg-sky-500 text-slate-950 shadow-[0_0_20px_rgba(56,189,248,0.4)]'
                : 'bg-slate-900/80 text-slate-400 hover:text-white border border-slate-800'
            }`}
          >
            <Home className="w-4 h-4" />
            <span>Assistant</span>
          </button>

          {/* Bluetooth Button */}
          <button
            onClick={() => setActiveTab('bluetooth')}
            className={`py-3 px-4 rounded-2xl flex items-center justify-center space-x-2 font-semibold text-sm transition-all duration-200 ${
              activeTab === 'bluetooth'
                ? 'bg-sky-500 text-slate-950 shadow-[0_0_20px_rgba(56,189,248,0.4)]'
                : 'bg-slate-900/80 text-slate-400 hover:text-white border border-slate-800'
            }`}
          >
            <Bluetooth className="w-4 h-4" />
            <span>Bluetooth</span>
          </button>
        </div>
      </footer>

      {/* ACCESSIBILITY PERMISSION MODAL
          "aani silect kel ki acesbility chi permition magel ti pan add kar oky" */}
      {showAccessibilityModal && (
        <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 animate-fade-in">
          <div className="bg-[#101827] border border-sky-500/40 rounded-3xl w-full max-w-md p-6 shadow-2xl relative">
            <button
              onClick={() => {
                setShowAccessibilityModal(false);
                setPendingAssistant(null);
              }}
              className="absolute top-4 right-4 text-slate-400 hover:text-white p-1 rounded-lg"
            >
              <X className="w-5 h-5" />
            </button>

            {/* Shield Icon */}
            <div className="w-14 h-14 rounded-2xl bg-sky-500/10 border border-sky-500/30 flex items-center justify-center text-sky-400 mb-4 mx-auto">
              <Shield className="w-7 h-7" />
            </div>

            <div className="text-center mb-5">
              <h3 className="text-lg font-bold text-white">
                Accessibility Permission Required
              </h3>
              <p className="text-xs text-slate-400 mt-1.5 leading-relaxed">
                To swipe up when you say <strong className="text-sky-400 font-bold">"{pendingAssistant || selectedAssistant}"</strong>, Rider Assistant needs permission to dispatch touch gestures at the screen center.
              </p>
            </div>

            <div className="bg-slate-950/70 border border-slate-800 rounded-2xl p-4 text-xs space-y-2.5 mb-6 text-slate-300">
              <div className="flex items-start space-x-2.5">
                <CheckCircle2 className="w-4 h-4 text-sky-400 flex-shrink-0 mt-0.5" />
                <span>Perform upward swipe gesture when wake-word is detected</span>
              </div>
              <div className="flex items-start space-x-2.5">
                <CheckCircle2 className="w-4 h-4 text-sky-400 flex-shrink-0 mt-0.5" />
                <span>Hands-free voice control while riding or driving</span>
              </div>
              <div className="flex items-start space-x-2.5">
                <CheckCircle2 className="w-4 h-4 text-emerald-400 flex-shrink-0 mt-0.5" />
                <span>Zero screen content reading — full user privacy maintained</span>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <button
                onClick={() => {
                  setShowAccessibilityModal(false);
                  setPendingAssistant(null);
                  showToast('Accessibility permission denied', 2000);
                }}
                className="py-3 px-4 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-semibold transition-colors"
              >
                Deny
              </button>
              <button
                onClick={confirmAccessibilityPermission}
                className="py-3 px-4 rounded-xl bg-sky-500 hover:bg-sky-400 text-slate-950 text-xs font-bold transition-all shadow-[0_0_20px_rgba(56,189,248,0.4)]"
              >
                Allow & Enable
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}

// -------------------------------------------------------------
// HIGH-QUALITY EARBUDS & CASE SVG ILLUSTRATIONS
// -------------------------------------------------------------

function LeftEarbudSvg() {
  return (
    <svg viewBox="0 0 100 130" className="w-full h-full drop-shadow-[0_8px_16px_rgba(0,0,0,0.6)]">
      <defs>
        <linearGradient id="budLeftGrad" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#ffffff" />
          <stop offset="60%" stopColor="#e2e8f0" />
          <stop offset="100%" stopColor="#94a3b8" />
        </linearGradient>
        <linearGradient id="budAccent" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stopColor="#38bdf8" />
          <stop offset="100%" stopColor="#0284c7" />
        </linearGradient>
      </defs>
      {/* Earbud Stem */}
      <rect x="52" y="50" width="14" height="65" rx="7" fill="url(#budLeftGrad)" />
      {/* Stem bottom metallic contact */}
      <rect x="52" y="108" width="14" height="7" rx="3.5" fill="#64748b" />
      {/* Earbud Head (curving inward to left) */}
      <circle cx="45" cy="42" r="26" fill="url(#budLeftGrad)" />
      {/* Silicone ear tip pointing left */}
      <ellipse cx="24" cy="42" rx="14" ry="19" fill="#f1f5f9" opacity="0.9" />
      {/* Speaker grill mesh */}
      <ellipse cx="14" cy="42" rx="4" ry="11" fill="#334155" />
      {/* Microphone vent */}
      <circle cx="59" cy="32" r="2.5" fill="#0f172a" />
      {/* Touch sensor bar with sky accent */}
      <rect x="56" y="62" width="6" height="24" rx="3" fill="url(#budAccent)" opacity="0.9" />
      {/* 'L' text */}
      <text x="59" y="100" fill="#64748b" fontSize="8" fontWeight="bold" textAnchor="middle">L</text>
    </svg>
  );
}

function RightEarbudSvg() {
  return (
    <svg viewBox="0 0 100 130" className="w-full h-full drop-shadow-[0_8px_16px_rgba(0,0,0,0.6)]">
      <defs>
        <linearGradient id="budRightGrad" x1="100%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" stopColor="#ffffff" />
          <stop offset="60%" stopColor="#e2e8f0" />
          <stop offset="100%" stopColor="#94a3b8" />
        </linearGradient>
      </defs>
      {/* Earbud Stem */}
      <rect x="34" y="50" width="14" height="65" rx="7" fill="url(#budRightGrad)" />
      {/* Stem bottom metallic contact */}
      <rect x="34" y="108" width="14" height="7" rx="3.5" fill="#64748b" />
      {/* Earbud Head (curving inward to right) */}
      <circle cx="55" cy="42" r="26" fill="url(#budRightGrad)" />
      {/* Silicone ear tip pointing right */}
      <ellipse cx="76" cy="42" rx="14" ry="19" fill="#f1f5f9" opacity="0.9" />
      {/* Speaker grill mesh */}
      <ellipse cx="86" cy="42" rx="4" ry="11" fill="#334155" />
      {/* Microphone vent */}
      <circle cx="41" cy="32" r="2.5" fill="#0f172a" />
      {/* Touch sensor bar with sky accent */}
      <rect x="38" y="62" width="6" height="24" rx="3" fill="#38bdf8" opacity="0.9" />
      {/* 'R' text */}
      <text x="41" y="100" fill="#64748b" fontSize="8" fontWeight="bold" textAnchor="middle">R</text>
    </svg>
  );
}

function EarbudsCaseSvg({ open = false, charging = false }: { open?: boolean; charging?: boolean }) {
  return (
    <svg viewBox="0 0 100 100" className="w-full h-full drop-shadow-[0_12px_24px_rgba(0,0,0,0.7)]">
      <defs>
        <linearGradient id="caseGrad" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#f8fafc" />
          <stop offset="40%" stopColor="#e2e8f0" />
          <stop offset="100%" stopColor="#64748b" />
        </linearGradient>
        <linearGradient id="caseInsideGrad" x1="0%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" stopColor="#0f172a" />
          <stop offset="100%" stopColor="#1e293b" />
        </linearGradient>
      </defs>

      {open ? (
        <>
          {/* Opened Case Lid raised */}
          <path
            d="M 18,36 C 18,16 82,16 82,36 Z"
            fill="url(#caseGrad)"
            stroke="#cbd5e1"
            strokeWidth="1.5"
          />
          {/* Case Interior Cavity */}
          <rect x="20" y="34" width="60" height="24" rx="6" fill="url(#caseInsideGrad)" />
          {/* Left Bud Slot */}
          <ellipse cx="36" cy="44" rx="10" ry="6" fill="#020617" />
          {/* Right Bud Slot */}
          <ellipse cx="64" cy="44" rx="10" ry="6" fill="#020617" />
          {/* Main Lower Body */}
          <rect x="18" y="44" width="64" height="46" rx="18" fill="url(#caseGrad)" />
          {/* Hinge Line */}
          <line x1="22" y1="44" x2="78" y2="44" stroke="#94a3b8" strokeWidth="1" />
        </>
      ) : (
        <>
          {/* Closed Sleek Case */}
          <rect x="16" y="22" width="68" height="64" rx="24" fill="url(#caseGrad)" />
          {/* Lid seam line */}
          <line x1="16" y1="46" x2="84" y2="46" stroke="#94a3b8" strokeWidth="1.5" />
          {/* Opening Notch */}
          <rect x="42" y="44" width="16" height="4" rx="2" fill="#cbd5e1" />
        </>
      )}

      {/* Front LED Indicator Light */}
      <circle
        cx="50"
        cy="64"
        r="2.5"
        fill={charging ? '#38bdf8' : open ? '#34d399' : '#94a3b8'}
        className={charging || open ? 'animate-pulse' : ''}
      />
    </svg>
  );
}
