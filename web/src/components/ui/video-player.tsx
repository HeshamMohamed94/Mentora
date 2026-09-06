"use client";

import { useEffect, useRef, useState } from "react";
import { Icon } from "./icon";

const PLAYBACK_SPEEDS = [0.5, 0.75, 1, 1.25, 1.5, 2];

export interface VideoPlayerLabels {
  play: string;
  pause: string;
  mute: string;
  unmute: string;
  volume: string;
  fullscreen: string;
  exitFullscreen: string;
  speed: string;
  speedValue: (speed: number) => string;
  loading: string;
  errorTitle: string;
  retry: string;
}

export interface VideoPlayerProps {
  src: string | null;
  title: string;
  labels: VideoPlayerLabels;
  initialPositionSeconds?: number;
  onEnded?: () => void;
  onPositionChange?: (positionSeconds: number) => void;
}

function timeLabel(seconds: number): string {
  if (!Number.isFinite(seconds)) return "0:00";
  const minutes = Math.floor(seconds / 60);
  return `${minutes}:${String(Math.floor(seconds % 60)).padStart(2, "0")}`;
}

export function VideoPlayer({
  src,
  title,
  labels,
  initialPositionSeconds = 0,
  onEnded,
  onPositionChange,
}: VideoPlayerProps) {
  const frameRef = useRef<HTMLDivElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const initialPosition = useRef(initialPositionSeconds);
  const lastReportedPosition = useRef(initialPositionSeconds);
  const [loading, setLoading] = useState(Boolean(src));
  const [playbackError, setPlaybackError] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [buffered, setBuffered] = useState(0);
  const [volume, setVolume] = useState(1);
  const [muted, setMuted] = useState(false);
  const [speed, setSpeed] = useState(1);
  const [speedOpen, setSpeedOpen] = useState(false);
  const [fullscreen, setFullscreen] = useState(false);

  useEffect(() => {
    setLoading(Boolean(src));
    setPlaybackError(false);
    setPlaying(false);
    setCurrentTime(0);
    setDuration(0);
    setBuffered(0);
    lastReportedPosition.current = initialPosition.current;
  }, [src]);

  useEffect(() => {
    const updateFullscreen = () => setFullscreen(document.fullscreenElement === frameRef.current);
    document.addEventListener("fullscreenchange", updateFullscreen);
    return () => document.removeEventListener("fullscreenchange", updateFullscreen);
  }, []);

  async function togglePlayback() {
    const video = videoRef.current;
    if (!video) return;
    if (video.paused) await video.play();
    else video.pause();
  }

  function updateTimeline() {
    const video = videoRef.current;
    if (!video) return;
    setCurrentTime(video.currentTime);
    if (video.buffered.length > 0 && video.duration > 0) {
      setBuffered((video.buffered.end(video.buffered.length - 1) / video.duration) * 100);
    }
    if (video.currentTime - lastReportedPosition.current >= 15) {
      lastReportedPosition.current = video.currentTime;
      onPositionChange?.(Math.floor(video.currentTime));
    }
  }

  function loadMetadata() {
    const video = videoRef.current;
    if (!video) return;
    setDuration(video.duration);
    video.currentTime = Math.min(initialPosition.current, video.duration || initialPosition.current);
    setCurrentTime(video.currentTime);
  }

  function seek(positionSeconds: number) {
    if (!videoRef.current) return;
    videoRef.current.currentTime = positionSeconds;
    setCurrentTime(positionSeconds);
  }

  function changeVolume(level: number) {
    if (!videoRef.current) return;
    videoRef.current.volume = level;
    videoRef.current.muted = level === 0;
    setVolume(level);
    setMuted(level === 0);
  }

  function toggleMuted() {
    if (!videoRef.current) return;
    const nextMuted = !muted;
    videoRef.current.muted = nextMuted;
    setMuted(nextMuted);
  }

  function changeSpeed(nextSpeed: number) {
    if (videoRef.current) videoRef.current.playbackRate = nextSpeed;
    setSpeed(nextSpeed);
    setSpeedOpen(false);
  }

  async function toggleFullscreen() {
    if (!frameRef.current) return;
    if (document.fullscreenElement) await document.exitFullscreen();
    else await frameRef.current.requestFullscreen();
  }

  function retryPlayback() {
    setPlaybackError(false);
    setLoading(true);
    videoRef.current?.load();
  }

  function finishPlayback() {
    setPlaying(false);
    onPositionChange?.(Math.floor(duration));
    onEnded?.();
  }

  if (!src) return null;

  const playedPercent = duration > 0 ? (currentTime / duration) * 100 : 0;

  return (
    <div ref={frameRef} className="mtx-video-frame">
      <video
        ref={videoRef}
        className="mtx-video-media"
        src={src}
        preload="metadata"
        onCanPlay={() => setLoading(false)}
        onLoadedMetadata={loadMetadata}
        onPlay={() => setPlaying(true)}
        onPause={() => {
          setPlaying(false);
          if (!videoRef.current?.ended) onPositionChange?.(Math.floor(videoRef.current?.currentTime ?? 0));
        }}
        onTimeUpdate={updateTimeline}
        onEnded={finishPlayback}
        onError={() => {
          setLoading(false);
          setPlaybackError(true);
        }}
      />

      {loading && <div className="mtx-video-overlay mtx-skeleton" role="status" aria-label={labels.loading} />}
      {playbackError && (
        <div className="mtx-video-overlay mtx-video-error" role="alert">
          <Icon name="cancel" />
          <p className="mtx-text-heading-h4">{labels.errorTitle}</p>
          <button type="button" className="mtx-video-retry" onClick={retryPlayback}>{labels.retry}</button>
        </div>
      )}

      {!playbackError && (
        <div className="mtx-video-controls">
          <p className="mtx-video-title mtx-text-caption" title={title}>{title}</p>
          <div className="mtx-video-timeline" dir="ltr">
            <span className="mtx-text-caption">{timeLabel(currentTime)}</span>
            <div className="mtx-video-scrubber">
              <span className="mtx-video-scrubber-buffered" style={{ width: `${buffered}%` }} />
              <span className="mtx-video-scrubber-played" style={{ width: `${playedPercent}%` }} />
              <input
                type="range"
                min={0}
                max={duration || 0}
                step={0.1}
                value={currentTime}
                disabled={!duration}
                aria-label={title}
                onChange={(event) => seek(Number(event.target.value))}
              />
            </div>
            <span className="mtx-text-caption">{timeLabel(duration)}</span>
          </div>
          <div className="mtx-video-actions">
            <button type="button" className="mtx-video-control" onClick={togglePlayback} disabled={loading} aria-label={playing ? labels.pause : labels.play}>
              <Icon name={playing ? "pause" : "play"} />
            </button>
            <button type="button" className="mtx-video-control" onClick={toggleMuted} disabled={loading} aria-label={muted ? labels.unmute : labels.mute}>
              <Icon name={muted || volume === 0 ? "volumeMuted" : "volumeOn"} />
            </button>
            <input className="mtx-video-volume" dir="ltr" type="range" min={0} max={1} step={0.05} value={muted ? 0 : volume} disabled={loading} aria-label={labels.volume} onChange={(event) => changeVolume(Number(event.target.value))} />
            <div className="mtx-video-speed">
              <button type="button" className="mtx-video-control mtx-video-speed-trigger" onClick={() => setSpeedOpen((open) => !open)} aria-label={labels.speed} aria-expanded={speedOpen}>
                {labels.speedValue(speed)}
              </button>
              {speedOpen && (
                <div className="mtx-video-speed-menu">
                  {PLAYBACK_SPEEDS.map((playbackSpeed) => (
                    <button type="button" key={playbackSpeed} data-selected={speed === playbackSpeed || undefined} onClick={() => changeSpeed(playbackSpeed)}>{labels.speedValue(playbackSpeed)}</button>
                  ))}
                </div>
              )}
            </div>
            <button type="button" className="mtx-video-control" onClick={toggleFullscreen} aria-label={fullscreen ? labels.exitFullscreen : labels.fullscreen}>
              <Icon name={fullscreen ? "fullscreenExit" : "fullscreen"} />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
