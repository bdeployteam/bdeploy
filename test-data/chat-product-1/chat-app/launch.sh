#!/usr/bin/env bash
echo "This is a simple chat application. It can print a menu or echo your input..."

echo "(enter m for menu)"

while : ; do
    echo -n "# "
	read cmd

	if [[ $? != 0 || $cmd == "" ]]; then
		echo bye.
		exit 0
	elif [[ $cmd == "c" ]]; then
		for c in {0..255} ; do
       			printf "\e[48;5;%sm   %3s   \e[0m" $c $c
       			if [ $((($c + 1) % 6)) == 4 ] ; then echo; fi
    		done
    		echo
	elif [[ $cmd == "m" ]]; then
		echo "-----------------------------------"
		echo "Known commands:"
		echo "    m: print this menu"
		echo "    c: print a color map"
		echo "    [other input]: echo your input"
		echo "    [empty input]: exit"
		echo "-----------------------------------"
	else
		echo 'you said: "'${cmd}'"'
	fi
done

exit 0

