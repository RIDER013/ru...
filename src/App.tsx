import React, { useState, useEffect } from 'react';
import { Bluetooth, ChevronLeft, Power, Sparkles, X } from 'lucide-react';

type TestType = 'voice' | 'proxymity' | 'blutooth';

export default function App() {
  // Navigation & State
  const [currentScreen, setCurrentScreen] = useState<'main' | 'test_screen'>('main');
  const [activeTest, setActiveTest] = useState<TestType>('proxymity');

  // Start Window State
  const [showStartWindow, setShowStartWindow] = useState(false);
  const [voiceOn, setVoiceOn] = useState(true);
  const [proxymityOn, setProxymityOn] = useState(true);
  const [blutoothOn, setBlutoothOn] = useState(true);

  // Test Selection State
  const [showTestMenu, setShowTestMenu] = useState(false);

  // Connectivity & Sensor Detection States
  // Real check: Bluetooth is not connected by default unless connected
  const [isBluetoothConnected, setIsBluetoothConnected] = useState(false);
  const [isSensorActive, setIsSensorActive] = useState(false);
  const [voiceDetected, setVoiceDetected] = useState(false);

  // Toast for feedback
  const [toastMsg, setToastMsg] = useState<string | null>(null);

  const showToast = (msg: string) => {
    setToastMsg(msg);
    setTimeout(() => setToastMsg(null), 2500);
  };

  // Open Full-Screen Test for specific item
  const openTestScreen = (test: TestType) => {
    setActiveTest(test);
    setIsSensorActive(false);
    setVoiceDetected(false);
    setShowTestMenu(false);
    setCurrentScreen('test_screen');
  };

  // Determine connectivity status for the active test
  const getIsConnected = () => {
    if (activeTest === 'blutooth') {
      return isBluetoothConnected;
    }
    if (activeTest === 'proxymity') {
      return isSensorActive;
    }
    if (activeTest === 'voice') {
      return voiceDetected;
    }
    return false;
  };

  const isConnected = getIsConnected();

  return (
    <div className="min-h-screen w-full bg-[#080C14] text-white flex flex-col items-center justify-between font-sans selection:bg-pink-500/30 overflow-x-hidden">
      
      {/* Toast Feedback */}
      {toastMsg && (
        <div className="fixed top-5 z-50 px-4 py-2 rounded-full bg-slate-900 border border-sky-400 text-sky-300 text-xs shadow-2xl backdrop-blur-md animate-fade-in flex items-center space-x-2">
          <Sparkles className="w-3.5 h-3.5 text-sky-400" />
          <span>{toastMsg}</span>
        </div>
      )}

      {/* Main App Container */}
      <div className="relative w-full max-w-sm flex-1 flex flex-col justify-between p-6 bg-[#0B0F19] shadow-2xl border-x border-slate-900">
        
        {/* ============================================================== */}
        {/* SCREEN 1: MAIN SCREEN                                          */}
        {/* ============================================================== */}
        {currentScreen === 'main' ? (
          <div className="flex-1 flex flex-col justify-between">
            
            {/* TOP HEADER: ƦƗƉƐƦ Title + Top Right [Ʀ] in Pink + Bluetooth Icon below it */}
            <div className="w-full flex items-start justify-between pt-2">
              {/* Title ƦƗƉƐƦ */}
              <h1 className="text-2xl font-black text-white tracking-widest select-none">
                ƦƗƉƐƦ
              </h1>

              {/* Top Right: Ʀ (Pink) & Bluetooth Icon below it */}
              <div className="flex flex-col items-center space-y-2">
                {/* Ʀ in Pink Color */}
                <span className="text-2xl font-black text-[#FF2A85] select-none leading-none">
                  Ʀ
                </span>

                {/* Bluetooth Icon below Ʀ: Display only (Colourful Sky Blue if connected, White if not) */}
                <div
                  className="p-1 select-none pointer-events-none"
                  aria-label="Bluetooth Status"
                >
                  <Bluetooth
                    className={`w-5 h-5 transition-colors ${
                      isBluetoothConnected
                        ? 'text-[#38BDF8] drop-shadow-[0_0_8px_#38BDF8]'
                        : 'text-white opacity-90'
                    }`}
                  />
                </div>
              </div>
            </div>

            {/* CENTER: START BUTTON */}
            <div className="flex-1 flex flex-col items-center justify-center my-8">
              <button
                onClick={() => setShowStartWindow(true)}
                className="w-28 h-28 rounded-full bg-gradient-to-tr from-[#0284C7] to-[#38BDF8] hover:scale-105 active:scale-95 text-white flex flex-col items-center justify-center shadow-[0_0_35px_rgba(56,189,248,0.4)] transition-all cursor-pointer border-2 border-sky-300"
                title="Start"
              >
                <Power className="w-9 h-9 stroke-[2.5]" />
                <span className="text-xs font-black tracking-widest mt-1">START</span>
              </button>
            </div>

            {/* TEST SECTION & BOTTOM */}
            <div className="w-full flex flex-col items-center space-y-4 pb-2">
              
              {/* TEST BUTTON */}
              <div className="w-full relative">
                <button
                  onClick={() => setShowTestMenu(!showTestMenu)}
                  className="w-full py-3.5 px-4 rounded-xl bg-[#1E293B] hover:bg-slate-700 active:scale-98 text-white font-bold text-sm tracking-wider shadow-lg border border-slate-700 transition-all cursor-pointer flex items-center justify-center space-x-2"
                >
                  <span>test</span>
                </button>

                {/* Dropdown / Menu when TEST is clicked:
                    1 ] voice             test
                    2 ] proxymity    test
                    3 ] blutooth       test */}
                {showTestMenu && (
                  <div className="absolute bottom-full mb-2 w-full bg-[#101827] border-2 border-slate-700 rounded-2xl p-3 shadow-2xl space-y-2 z-30 animate-fade-in">
                    <button
                      onClick={() => openTestScreen('voice')}
                      className="w-full py-2.5 px-4 rounded-xl bg-slate-800 hover:bg-slate-700 flex items-center justify-between text-xs font-bold text-slate-200 cursor-pointer active:scale-98 transition-all"
                    >
                      <span>1 ] voice</span>
                      <span className="text-[#38BDF8] uppercase font-mono">test</span>
                    </button>

                    <button
                      onClick={() => openTestScreen('proxymity')}
                      className="w-full py-2.5 px-4 rounded-xl bg-slate-800 hover:bg-slate-700 flex items-center justify-between text-xs font-bold text-slate-200 cursor-pointer active:scale-98 transition-all"
                    >
                      <span>2 ] proxymity</span>
                      <span className="text-[#38BDF8] uppercase font-mono">test</span>
                    </button>

                    <button
                      onClick={() => openTestScreen('blutooth')}
                      className="w-full py-2.5 px-4 rounded-xl bg-slate-800 hover:bg-slate-700 flex items-center justify-between text-xs font-bold text-slate-200 cursor-pointer active:scale-98 transition-all"
                    >
                      <span>3 ] blutooth</span>
                      <span className="text-[#38BDF8] uppercase font-mono">test</span>
                    </button>
                  </div>
                )}
              </div>

              {/* BOTTOM TEXT: created by ƦƗƉƐƦ in small letters */}
              <p className="text-[10px] text-slate-400 font-mono tracking-wider select-none pt-1">
                created by ƦƗƉƐƦ
              </p>
            </div>

          </div>
        ) : (
          /* ============================================================== */
          /* SCREEN 2: FULL SCREEN TEST (voice / proxymity / blutooth)      */
          /* "tyat fkt asel click sensor aani khali asel conectyvity        */
          /*  aani white colur cha dot jar conectyvity milali tar dot       */
          /*  sky blue hoil oky asach blutooth aani voice cha pan oky       */
          /*  aani conectyvity nasel tar not conectyvity as lihlel asel"   */
          /* ============================================================== */
          <div className="flex-1 flex flex-col justify-between py-2">
            
            {/* Top Bar with Back Button */}
            <div className="w-full flex items-center justify-between border-b border-slate-800/80 pb-3">
              <button
                onClick={() => setCurrentScreen('main')}
                className="flex items-center space-x-1 text-xs text-slate-400 hover:text-white cursor-pointer"
              >
                <ChevronLeft className="w-4 h-4" />
                <span>back</span>
              </button>

              <span className="text-xs font-bold text-slate-300 uppercase tracking-widest font-mono">
                {activeTest}
              </span>
            </div>

            {/* Center Content:
                conectyvity
                dot (White -> Sky Blue when connected, else 'not conectyvity') */}
            <div className="flex-1 flex flex-col items-center justify-center space-y-7">
              
              {/* Connectivity section */}
              <div
                onClick={() => {
                  if (activeTest === 'proxymity') {
                    const next = !isSensorActive;
                    setIsSensorActive(next);
                    showToast(next ? 'Sensor Connected!' : 'Sensor Idle');
                  } else if (activeTest === 'voice') {
                    const next = !voiceDetected;
                    setVoiceDetected(next);
                    showToast(next ? 'Voice Heard!' : 'Voice Idle');
                  } else if (activeTest === 'blutooth') {
                    const next = !isBluetoothConnected;
                    setIsBluetoothConnected(next);
                    showToast(next ? 'Bluetooth Connected' : 'Bluetooth Disconnected');
                  }
                }}
                className="flex flex-col items-center space-y-3 cursor-pointer select-none p-4 rounded-2xl hover:bg-slate-900/40 transition-colors"
                title="Tap to toggle status test"
              >
                <span className="text-sm font-semibold tracking-wider text-slate-300">
                  conectyvity
                </span>

                {/* Dot:
                    - White colour initially / when disconnected
                    - Sky Blue (#38BDF8) when connectivity is active */}
                <div
                  className={`w-6 h-6 rounded-full transition-all duration-300 border-2 ${
                    isConnected
                      ? 'bg-[#38BDF8] border-sky-300 shadow-[0_0_22px_#38BDF8] scale-110'
                      : 'bg-white border-slate-300 shadow-sm'
                  }`}
                />

                {/* If connectivity is NOT present: show "not conectyvity" */}
                {!isConnected && (
                  <span className="text-xs text-[#EF4444] font-medium tracking-wide">
                    not conectyvity
                  </span>
                )}
              </div>

            </div>

            {/* Bottom: Return to main */}
            <div className="w-full flex justify-center pb-2">
              <p className="text-[10px] text-slate-500 font-mono tracking-wider select-none">
                created by ƦƗƉƐƦ
              </p>
            </div>

          </div>
        )}

      </div>

      {/* ============================================================== */}
      {/* START WINDOW (MODAL):                                          */}
      {/* 1 ] voice             on                                       */}
      {/* 2 ] proxymity    on                                            */}
      {/* 3 ] blutooth   on                                              */}
      {/* ============================================================== */}
      {showStartWindow && (
        <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-[#101827] border-2 border-slate-700 rounded-3xl w-full max-w-xs p-5 shadow-2xl space-y-4 animate-scale-in">
            
            <div className="flex items-center justify-between border-b border-slate-800 pb-2">
              <span className="text-sm font-bold text-white tracking-wide">
                Start Configuration
              </span>
              <button
                onClick={() => setShowStartWindow(false)}
                className="text-slate-400 hover:text-white p-1 cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* 1 ] voice on */}
            <div className="flex items-center justify-between py-2 border-b border-slate-800/80">
              <span className="text-xs font-bold text-slate-200">
                1 ] voice
              </span>
              <button
                onClick={() => setVoiceOn(!voiceOn)}
                className={`px-3 py-1 rounded-full text-xs font-bold uppercase transition-all cursor-pointer ${
                  voiceOn ? 'bg-[#38BDF8] text-[#080C14]' : 'bg-slate-800 text-slate-400'
                }`}
              >
                {voiceOn ? 'on' : 'off'}
              </button>
            </div>

            {/* 2 ] proxymity on */}
            <div className="flex items-center justify-between py-2 border-b border-slate-800/80">
              <span className="text-xs font-bold text-slate-200">
                2 ] proxymity
              </span>
              <button
                onClick={() => setProxymityOn(!proxymityOn)}
                className={`px-3 py-1 rounded-full text-xs font-bold uppercase transition-all cursor-pointer ${
                  proxymityOn ? 'bg-[#38BDF8] text-[#080C14]' : 'bg-slate-800 text-slate-400'
                }`}
              >
                {proxymityOn ? 'on' : 'off'}
              </button>
            </div>

            {/* 3 ] blutooth on */}
            <div className="flex items-center justify-between py-2 border-b border-slate-800/80">
              <span className="text-xs font-bold text-slate-200">
                3 ] blutooth
              </span>
              <button
                onClick={() => setBlutoothOn(!blutoothOn)}
                className={`px-3 py-1 rounded-full text-xs font-bold uppercase transition-all cursor-pointer ${
                  blutoothOn ? 'bg-[#38BDF8] text-[#080C14]' : 'bg-slate-800 text-slate-400'
                }`}
              >
                {blutoothOn ? 'on' : 'off'}
              </button>
            </div>

            {/* Done button */}
            <div className="pt-2">
              <button
                onClick={() => {
                  setShowStartWindow(false);
                  showToast('ƦƗƉƐƦ Active in background!');
                }}
                className="w-full py-2.5 rounded-xl bg-[#0284C7] hover:bg-sky-500 text-white font-bold text-xs tracking-wider shadow cursor-pointer transition-all"
              >
                Done
              </button>
            </div>

          </div>
        </div>
      )}

    </div>
  );
}
