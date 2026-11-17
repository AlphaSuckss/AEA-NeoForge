package dev.alphasucks.aea.mindcontrol;

/** Added to every mob: whether a mind control session is driving it, so its AI stays off. */
public interface MindControlled {
    boolean aea$mindControlled();

    void aea$setMindControlled(boolean controlled);
}
