import React, { useState, useEffect, useRef } from 'react';
import {
  Home,
  Bluetooth,
  ChevronUp,
  Battery,
  BatteryCharging,
  Shield,
  Play,
  Volume2,
  Sparkles,
  Check,
  X,
  Repeat
} from 'lucide-react';

type AssistantName = 'RIDER' | 'BUDDY';
type RemoteRole = 'BOTH' | 'LEFT' | 'RIGHT';
type AudioOutput = 'STEREO' | 'LEFT' | 'RIGHT';

interface EarbudDevice {
  name: string;
  connected: boolean;
  leftBattery: number;
  rightBattery: number;
  caseBattery: number;
  address: string;
}

export default function App() {
  const [activeTab, setActiveTab] = useState<'home' | 'bluetooth'>('home');

  // Assistant & Wake-word State
  const [selectedAssistant, setSelectedAssistant] = useState<AssistantName>('RIDER');
  const [accessibilityGranted, setAccessibilityGranted] = useState<boolean>(false);
  const [showAccessibilityModal, setShowAccessibilityModal] = useState<boolean>(false);

  // Background Service Active State
  const [serviceRunning, setServiceRunning] = useState<boolean>(false);

  // Earbuds Remote & Audio Routing Settings
  const [remoteEarbud, setRemoteEarbud] = useState<RemoteRole>('BOTH');
  const [audioRouting, setAudioRouting] = useState<AudioOutput>('STEREO');

  // Gesture Swipe State
  const [isSwiping, setIsSwiping] = useState<boolean>(false);
  const [swipeSource, setSwipeSource] = useState<string>('');
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const toastTimeoutRef = useRef<number | null>(null);

  // Bluetooth Earbuds State
  const [device, setDevice] = useState<EarbudDevice>({
    name: 'Rider AirBuds Pro',
    connected: true,
    leftBattery: 85,
    rightBattery: 90,
    caseBattery: 95,
    address: '4A:26:9F:8B:12:00',
  });

  const showToast = (msg: string, dur = 3000) => {
    if (toastTimeoutRef.current) window.clearTimeout(toastTimeoutRef.current);
    setToastMessage(msg);
    toastTimeoutRef.current = window.setTimeout(() => setToastMessage(null), dur);
  };

  // Trigger Center Upward Swipe Gesture
  const triggerCenterSwipe = (source: string) => {
    if (!accessibilityGranted) {
      setShowAccessibilityModal(true);
      showToast('Please enable Accessibility Permission to perform swipe gestures', 3500);
      return;
    }

    setSwipeSource(source);
    setIsSwiping(true);
    showToast(`"${source}" Swiped Up at Center!`, 2000);

    if (typeof navigator !== 'undefined' && navigator.vibrate) {
      navigator.vibrate([40, 50]);
    }

    setTimeout(() => {
      setIsSwiping(false);
      setSwipeSource('');
    }, 850);
  };

  // Small Play / Start button handler
  // "lets start te fkt ek lahans button asel tyavar kahi text nko"
  const handleStartToggle = () => {
    if (!accessibilityGranted) {
      setShowAccessibilityModal(true);
      showToast('Enable Accessibility Permission first', 3000);
      return;
    }

    setServiceRunning(true);
    showToast(`RIDER Active! Say "${selectedAssistant}" to swipe`, 3000);
  };

  // Single button toggle between Say Rider and Say Buddy
  // "tyat asel say riderr aani budy tyavar click kel ki donhi madheun change karaych option yeil oky"
  const handleToggleWakeWord = () => {
    const nextAssistant = selectedAssistant === 'RIDER' ? 'BUDDY' : 'RIDER';
    setSelectedAssistant(nextAssistant);
    showToast(`Switched to "Say ${nextAssistant}"! Tap again to switch`, 2000);
  };

  const confirmAccessibilityPermission = () => {
    setAccessibilityGranted(true);
    setShowAccessibilityModal(false);
    showToast(`Accessibility Permission Granted!`, 2500);
  };

  return (
    <div className="relative min-h-screen w-full bg-[#0b0f19] text-white flex flex-col justify-between font-sans overflow-x-hidden selection:bg-[#38bdf8]/30">
      
      {/* Toast Notification */}
      {toastMessage && (
        <div className="fixed top-5 left-1/2 -translate-x-1/2 z-50 px-4 py-2 rounded-full bg-slate-900/95 border border-sky-500/40 text-white text-xs shadow-[0_10px_30px_rgba(0,0,0,0.8)] backdrop-blur-md animate-fade-in flex items-center space-x-2">
          <Sparkles className="w-3.5 h-3.5 text-[#38bdf8] animate-spin" />
          <span>{toastMessage}</span>
        </div>
      )}

      {/* CENTER UPWARD SWIPE GESTURE ANIMATION OVERLAY */}
      {isSwiping && (
        <div className="fixed inset-0 pointer-events-none z-40 flex flex-col items-center justify-center">
          <div className="relative w-full h-full flex flex-col items-center justify-center">
            <div className="absolute w-72 h-72 rounded-full bg-sky-500/15 blur-3xl animate-pulse" />
            <div className="relative flex flex-col items-center">
              <div className="text-center mb-4">
                <span className="px-3.5 py-1 rounded-full bg-sky-500/20 border border-sky-400 text-sky-300 font-mono text-xs tracking-wider shadow-lg flex items-center space-x-1.5">
                  <ChevronUp className="w-3.5 h-3.5 animate-bounce" />
                  <span>SWIPING UP ({swipeSource})</span>
                </span>
              </div>
              <div className="relative w-1.5 h-64 bg-gradient-to-t from-sky-400/10 via-sky-400 to-white rounded-full shadow-[0_0_20px_#38bdf8] overflow-hidden flex flex-col justify-end">
                <div className="w-full bg-white h-20 rounded-full shadow-[0_0_15px_#ffffff] animate-ping" />
              </div>
              <div className="w-12 h-12 rounded-full border-3 border-sky-400 bg-sky-400/20 shadow-[0_0_25px_#38bdf8] animate-ping mt-1" />
            </div>
          </div>
        </div>
      )}

      {/* TOP HEADER: Clean & transparent, ribbon removed ("hi rebbon kaadh oky") */}
      <header className="w-full px-5 pt-5 pb-2 flex items-center justify-between">
        <div className="flex items-center space-x-2.5">
          <img
            src="/assets/aistudio/image_3001ac4a.png"
            alt="RIDER"
            className="w-8 h-8 rounded-xl object-cover border border-red-500/50 shadow-[0_0_10px_rgba(239,68,68,0.4)]"
          />
          <h1 className="text-xl font-black text-[#38bdf8] tracking-widest leading-none">
            RIDER
          </h1>
        </div>

        {/* Accessibility Status Pill */}
        <button
          onClick={() => setShowAccessibilityModal(true)}
          className={`px-2 py-0.5 rounded-full text-[10px] font-semibold border flex items-center space-x-1 transition-all ${
            accessibilityGranted
              ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400'
              : 'bg-amber-500/10 border-amber-500/30 text-amber-400 animate-pulse'
          }`}
        >
          <Shield className="w-3 h-3" />
          <span>{accessibilityGranted ? 'Active' : 'Permission'}</span>
        </button>
      </header>

      {/* MAIN CONTENT AREA */}
      <main className="flex-1 w-full max-w-sm mx-auto flex flex-col justify-center px-4 py-2">

        {/* ========================================================== */}
        {/* 1. HOME SCREEN: Minimal, small play button & Say button    */}
        {/* ========================================================== */}
        {activeTab === 'home' && (
          <div className="w-full flex-1 flex flex-col justify-center items-center py-6 space-y-7 animate-fade-in">

            {/* Small icon-only "LET'S START" button (no text)
                "lets start te fkt ek lahans button asel tyavar kahi text nko" */}
            <div className="flex flex-col items-center">
              <button
                onClick={handleStartToggle}
                className="w-16 h-16 rounded-full bg-gradient-to-tr from-sky-500 via-sky-400 to-cyan-300 hover:scale-105 active:scale-95 text-slate-950 flex items-center justify-center shadow-[0_0_25px_rgba(56,189,248,0.4)] transition-all cursor-pointer"
                title="Start RIDER"
              >
                <Play className="w-7 h-7 fill-slate-950 ml-0.5" />
              </button>
            </div>

            {/* Combined "Say Rider" / "Say Buddy" Interactive Button
                "fkt he button naavach aahe na te asu de tyat asel say riderr aani budy tyavar click kel ki donhi madheun change karaych option yeil oky" */}
            <div className="w-full flex flex-col items-center space-y-2.5">
              <button
                onClick={handleToggleWakeWord}
                className="w-full py-3.5 px-5 rounded-2xl bg-slate-900/90 hover:bg-slate-800 border border-slate-700/80 active:scale-98 text-white font-bold text-sm tracking-wide shadow-lg transition-all flex items-center justify-between group"
              >
                <div className="flex items-center space-x-2">
                  <span className="w-2 h-2 rounded-full bg-sky-400 animate-ping" />
                  <span className="text-white text-sm">
                    Say <span className="text-[#38bdf8] font-black">{selectedAssistant}</span>
                  </span>
                </div>

                <div className="flex items-center space-x-1.5 text-xs text-slate-400 group-hover:text-sky-300">
                  <Repeat className="w-3.5 h-3.5" />
                  <span className="text-[11px]">
                    Switch to {selectedAssistant === 'RIDER' ? 'Buddy' : 'Rider'}
                  </span>
                </div>
              </button>

              {/* Quick Test Trigger for current selection */}
              <button
                onClick={() => triggerCenterSwipe(`Say ${selectedAssistant}`)}
                className="text-xs text-sky-400 hover:text-sky-300 flex items-center space-x-1 py-1 px-3 rounded-lg hover:bg-slate-800/40 transition-colors"
              >
                <ChevronUp className="w-3.5 h-3.5" />
                <span>Test "Say {selectedAssistant}" Swipe</span>
              </button>
            </div>

          </div>
        )}

        {/* ========================================================== */}
        {/* 2. BLUETOOTH SCREEN: Compact Earbuds with Sensors & Modes */}
        {/* ========================================================== */}
        {activeTab === 'bluetooth' && (
          <div className="w-full flex-1 flex flex-col justify-center py-2 space-y-3.5 animate-fade-in">
            
            <div className="text-center">
              <h2 className="text-sm font-bold text-white tracking-wide">
                {device.name}
              </h2>
              <p className="text-[10px] text-sky-400 font-mono">
                Touch Sensors Active
              </p>
            </div>

            {/* Compact Earbuds & Case with Touch Sensors
                "tyache sensores aahet na tyavar click kel ki swip whav oky" */}
            <div className="grid grid-cols-3 gap-2 items-center">
              
              {/* Left Earbud */}
              <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-2 flex flex-col items-center">
                <span className="text-[9px] font-bold text-sky-400 mb-0.5">LEFT</span>
                <button
                  onClick={() => triggerCenterSwipe('Left Earbud Sensor')}
                  className="relative p-1 rounded-lg hover:bg-slate-800 transition-all text-center group cursor-pointer"
                  title="Click sensor to swipe"
                >
                  <div className="w-10 h-14 flex items-center justify-center">
                    <LeftEarbudSvg />
                  </div>
                  <span className="text-[8px] px-1 py-0.2 rounded bg-sky-500/20 text-sky-300 border border-sky-400/40 block mt-0.5 font-semibold">
                    Sensor
                  </span>
                </button>
                <div className="w-full mt-1 pt-1 border-t border-slate-800 text-center text-[10px] font-bold text-white flex items-center justify-center space-x-0.5">
                  <Battery className="w-3 h-3 text-emerald-400" />
                  <span>{device.leftBattery}%</span>
                </div>
              </div>

              {/* Case */}
              <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-2 flex flex-col items-center">
                <span className="text-[9px] font-bold text-slate-400 mb-0.5">CASE</span>
                <div className="w-10 h-14 flex items-center justify-center">
                  <EarbudsCaseSvg />
                </div>
                <div className="w-full mt-1 pt-1 border-t border-slate-800 text-center text-[10px] font-bold text-white flex items-center justify-center space-x-0.5">
                  <BatteryCharging className="w-3 h-3 text-sky-400" />
                  <span>{device.caseBattery}%</span>
                </div>
              </div>

              {/* Right Earbud */}
              <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-2 flex flex-col items-center">
                <span className="text-[9px] font-bold text-sky-400 mb-0.5">RIGHT</span>
                <button
                  onClick={() => triggerCenterSwipe('Right Earbud Sensor')}
                  className="relative p-1 rounded-lg hover:bg-slate-800 transition-all text-center group cursor-pointer"
                  title="Click sensor to swipe"
                >
                  <div className="w-10 h-14 flex items-center justify-center">
                    <RightEarbudSvg />
                  </div>
                  <span className="text-[8px] px-1 py-0.2 rounded bg-sky-500/20 text-sky-300 border border-sky-400/40 block mt-0.5 font-semibold">
                    Sensor
                  </span>
                </button>
                <div className="w-full mt-1 pt-1 border-t border-slate-800 text-center text-[10px] font-bold text-white flex items-center justify-center space-x-0.5">
                  <Battery className="w-3 h-3 text-emerald-400" />
                  <span>{device.rightBattery}%</span>
                </div>
              </div>

            </div>

            {/* Gesture Remote Configuration */}
            <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-2.5 space-y-1.5">
              <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider block">
                Earbud Remote Mode
              </span>
              <div className="grid grid-cols-3 gap-1">
                {(['BOTH', 'LEFT', 'RIGHT'] as RemoteRole[]).map((role) => (
                  <button
                    key={role}
                    onClick={() => {
                      setRemoteEarbud(role);
                      showToast(`Remote: ${role} Earbud`, 1500);
                    }}
                    className={`py-1 px-1.5 rounded-lg text-[10px] font-bold transition-all ${
                      remoteEarbud === role
                        ? 'bg-sky-500 text-slate-950 shadow-sm'
                        : 'bg-slate-800/60 text-slate-400 hover:text-white'
                    }`}
                  >
                    {role === 'BOTH' ? 'Both' : role}
                  </button>
                ))}
              </div>
            </div>

            {/* Audio Voice Feedback Routing */}
            <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-2.5 space-y-1.5">
              <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider block">
                Audio Output
              </span>
              <div className="grid grid-cols-3 gap-1">
                {(['STEREO', 'LEFT', 'RIGHT'] as AudioOutput[]).map((mode) => (
                  <button
                    key={mode}
                    onClick={() => {
                      setAudioRouting(mode);
                      showToast(`Audio: ${mode}`, 1500);
                    }}
                    className={`py-1 px-1.5 rounded-lg text-[10px] font-bold transition-all ${
                      audioRouting === mode
                        ? 'bg-sky-500 text-slate-950 shadow-sm'
                        : 'bg-slate-800/60 text-slate-400 hover:text-white'
                    }`}
                  >
                    {mode === 'STEREO' ? 'Stereo' : mode}
                  </button>
                ))}
              </div>
            </div>

          </div>
        )}

      </main>

      {/* BOTTOM NAVIGATION: Reduced height / Compact ribbon ("khlchi rebbon thodi lahan kar")
          AND "created by rider" text at the very bottom ("hw naav sarvat khali") */}
      <footer className="w-full max-w-sm mx-auto px-4 pt-1.5 pb-2 bg-[#0b0f19] border-t border-slate-800/60 sticky bottom-0 z-30">
        <div className="grid grid-cols-2 gap-2">
          <button
            onClick={() => setActiveTab('home')}
            className={`py-1.5 px-3 rounded-xl flex items-center justify-center space-x-1.5 font-bold text-xs transition-all ${
              activeTab === 'home'
                ? 'bg-sky-500 text-slate-950 shadow-sm'
                : 'bg-slate-900 text-slate-400 hover:text-white border border-slate-800'
            }`}
          >
            <Home className="w-3.5 h-3.5" />
            <span>RIDER</span>
          </button>

          <button
            onClick={() => setActiveTab('bluetooth')}
            className={`py-1.5 px-3 rounded-xl flex items-center justify-center space-x-1.5 font-bold text-xs transition-all ${
              activeTab === 'bluetooth'
                ? 'bg-sky-500 text-slate-950 shadow-sm'
                : 'bg-slate-900 text-slate-400 hover:text-white border border-slate-800'
            }`}
          >
            <Bluetooth className="w-3.5 h-3.5" />
            <span>Earbuds</span>
          </button>
        </div>

        {/* "created by rider" at the VERY BOTTOM ("hw naav sarvat khali") */}
        <p className="text-[9px] text-slate-500 text-center tracking-widest lowercase pt-1.5 font-mono select-none">
          created by rider
        </p>
      </footer>

      {/* ACCESSIBILITY PERMISSION MODAL */}
      {showAccessibilityModal && (
        <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 animate-fade-in">
          <div className="bg-[#101827] border border-sky-500/40 rounded-3xl w-full max-w-xs p-4 shadow-2xl relative">
            <button
              onClick={() => setShowAccessibilityModal(false)}
              className="absolute top-3 right-3 text-slate-400 hover:text-white p-1 rounded-lg"
            >
              <X className="w-4 h-4" />
            </button>

            <div className="w-10 h-10 rounded-2xl bg-sky-500/10 border border-sky-500/30 flex items-center justify-center text-sky-400 mb-2.5 mx-auto">
              <Shield className="w-5 h-5" />
            </div>

            <div className="text-center mb-3">
              <h3 className="text-sm font-bold text-white">
                Accessibility Permission
              </h3>
              <p className="text-xs text-slate-400 mt-1 leading-relaxed">
                Allow RIDER to perform hands-free upward swipe gestures when you say <strong className="text-sky-400 font-bold">"{selectedAssistant}"</strong>.
              </p>
            </div>

            <div className="grid grid-cols-2 gap-2 mt-4">
              <button
                onClick={() => setShowAccessibilityModal(false)}
                className="py-2 px-3 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-semibold"
              >
                Cancel
              </button>
              <button
                onClick={confirmAccessibilityPermission}
                className="py-2 px-3 rounded-xl bg-sky-500 hover:bg-sky-400 text-slate-950 text-xs font-bold shadow-md"
              >
                Allow
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}

// -------------------------------------------------------------
// COMPACT EARBUDS & CASE SVG ICONS
// -------------------------------------------------------------

function LeftEarbudSvg() {
  return (
    <svg viewBox="0 0 100 130" className="w-full h-full drop-shadow-[0_2px_4px_rgba(0,0,0,0.5)]">
      <defs>
        <linearGradient id="budLeftGrad" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#ffffff" />
          <stop offset="60%" stopColor="#e2e8f0" />
          <stop offset="100%" stopColor="#94a3b8" />
        </linearGradient>
      </defs>
      <rect x="52" y="50" width="14" height="65" rx="7" fill="url(#budLeftGrad)" />
      <rect x="52" y="108" width="14" height="7" rx="3.5" fill="#64748b" />
      <circle cx="45" cy="42" r="26" fill="url(#budLeftGrad)" />
      <ellipse cx="24" cy="42" rx="14" ry="19" fill="#f1f5f9" opacity="0.9" />
      <ellipse cx="14" cy="42" rx="4" ry="11" fill="#334155" />
      <rect x="55" y="60" width="8" height="26" rx="4" fill="#38bdf8" />
      <text x="59" y="100" fill="#64748b" fontSize="8" fontWeight="bold" textAnchor="middle">L</text>
    </svg>
  );
}

function RightEarbudSvg() {
  return (
    <svg viewBox="0 0 100 130" className="w-full h-full drop-shadow-[0_2px_4px_rgba(0,0,0,0.5)]">
      <defs>
        <linearGradient id="budRightGrad" x1="100%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" stopColor="#ffffff" />
          <stop offset="60%" stopColor="#e2e8f0" />
          <stop offset="100%" stopColor="#94a3b8" />
        </linearGradient>
      </defs>
      <rect x="34" y="50" width="14" height="65" rx="7" fill="url(#budRightGrad)" />
      <rect x="34" y="108" width="14" height="7" rx="3.5" fill="#64748b" />
      <circle cx="55" cy="42" r="26" fill="url(#budRightGrad)" />
      <ellipse cx="76" cy="42" rx="14" ry="19" fill="#f1f5f9" opacity="0.9" />
      <ellipse cx="86" cy="42" rx="4" ry="11" fill="#334155" />
      <rect x="37" y="60" width="8" height="26" rx="4" fill="#38bdf8" />
      <text x="41" y="100" fill="#64748b" fontSize="8" fontWeight="bold" textAnchor="middle">R</text>
    </svg>
  );
}

function EarbudsCaseSvg() {
  return (
    <svg viewBox="0 0 100 100" className="w-full h-full drop-shadow-[0_4px_8px_rgba(0,0,0,0.6)]">
      <defs>
        <linearGradient id="caseGrad" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#f8fafc" />
          <stop offset="50%" stopColor="#e2e8f0" />
          <stop offset="100%" stopColor="#64748b" />
        </linearGradient>
      </defs>
      <rect x="18" y="24" width="64" height="60" rx="20" fill="url(#caseGrad)" />
      <line x1="18" y1="46" x2="82" y2="46" stroke="#94a3b8" strokeWidth="1.5" />
      <rect x="42" y="44" width="16" height="4" rx="2" fill="#cbd5e1" />
      <circle cx="50" cy="62" r="2.5" fill="#38bdf8" />
    </svg>
  );
}
