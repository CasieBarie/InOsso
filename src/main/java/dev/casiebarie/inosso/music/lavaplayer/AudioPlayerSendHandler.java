package dev.casiebarie.inosso.music.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import net.dv8tion.jda.api.audio.AudioSendHandler;

import java.nio.ByteBuffer;

public class AudioPlayerSendHandler implements AudioSendHandler {
	private final AudioPlayer audioPlayer;
	private AudioFrame lastFrame;
	public AudioPlayerSendHandler(AudioPlayer audioPlayer) {this.audioPlayer = audioPlayer;}
	@Override public boolean canProvide() {lastFrame = audioPlayer.provide(); return lastFrame != null;}
	@Override public boolean isOpus() {return true;}
	@Override public ByteBuffer provide20MsAudio() {
		if(lastFrame == null) {return ByteBuffer.allocate(0);}
		return ByteBuffer.wrap(lastFrame.getData());
	}
}